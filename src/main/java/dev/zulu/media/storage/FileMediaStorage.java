package dev.zulu.media.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zulu.media.config.ZuluMediaProperties;
import dev.zulu.media.download.MediaDetails;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public final class FileMediaStorage {

    private static final String METADATA_FILE = "metadata.json";

    private final ZuluMediaProperties properties;
    private final ObjectMapper objectMapper;
    private final Path root;
    private final Path jobsRoot;
    private final Path cacheRoot;

    public FileMediaStorage(ZuluMediaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.root = properties.getStoragePath().toAbsolutePath().normalize();
        this.jobsRoot = root.resolve("jobs");
        this.cacheRoot = root.resolve("cache");
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(jobsRoot);
        Files.createDirectories(cacheRoot);
    }

    public Path root() {
        return root;
    }

    public Path createJobDirectory(UUID jobId) throws IOException {
        Path directory = jobsRoot.resolve(jobId.toString()).normalize();
        requireInside(directory, jobsRoot);
        return Files.createDirectories(directory);
    }

    public String cacheKey(String videoId, String format, String bitrate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((videoId + '\0' + format + '\0' + bitrate).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public synchronized Optional<StoredMedia> findCached(String cacheKey) throws IOException {
        Path directory = cacheDirectory(cacheKey);
        Path metadataPath = directory.resolve(METADATA_FILE);
        if (!Files.isRegularFile(metadataPath, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }

        CacheMetadata metadata;
        try {
            metadata = objectMapper.readValue(metadataPath.toFile(), CacheMetadata.class);
        } catch (IOException exception) {
            deleteTree(directory);
            return Optional.empty();
        }

        if (metadata.lastAccessedAt().plus(properties.getCacheTtl()).isBefore(Instant.now())) {
            deleteTree(directory);
            return Optional.empty();
        }

        Path mediaPath = directory.resolve(metadata.fileName()).normalize();
        if (!mediaPath.startsWith(directory)
            || !Files.isRegularFile(mediaPath, LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(mediaPath)) {
            deleteTree(directory);
            return Optional.empty();
        }

        long size = Files.size(mediaPath);
        MediaDetails details = withSize(metadata.details(), size);
        CacheMetadata accessed = new CacheMetadata(metadata.fileName(), details, metadata.createdAt(), Instant.now());
        writeMetadata(metadataPath, accessed);
        Files.setLastModifiedTime(directory, FileTime.from(accessed.lastAccessedAt()));

        return Optional.of(new StoredMedia(mediaPath, details));
    }

    public synchronized StoredMedia store(
        String cacheKey,
        Path source,
        MediaDetails details
    ) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw new IOException("Downloaded media is not a regular file");
        }

        Path directory = cacheDirectory(cacheKey);
        Files.createDirectories(directory);
        String extension = sanitizedExtension(details.format());
        Path target = directory.resolve("audio." + extension).normalize();
        requireInside(target, directory);
        moveReplacing(source, target);

        long size = Files.size(target);
        MediaDetails storedDetails = withSize(details, size);
        Instant now = Instant.now();
        CacheMetadata metadata = new CacheMetadata(target.getFileName().toString(), storedDetails, now, now);
        writeMetadata(directory.resolve(METADATA_FILE), metadata);
        Files.setLastModifiedTime(directory, FileTime.from(now));

        return new StoredMedia(target, storedDetails);
    }

    public Path findDownloadedAudio(Path jobDirectory, String format) throws IOException {
        Path normalizedJobDirectory = jobDirectory.toAbsolutePath().normalize();
        requireInside(normalizedJobDirectory, jobsRoot);
        String suffix = "." + sanitizedExtension(format).toLowerCase(java.util.Locale.ROOT);

        try (Stream<Path> files = Files.walk(normalizedJobDirectory, 2)) {
            return files
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> !Files.isSymbolicLink(path))
                .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(suffix))
                .max(Comparator.comparingLong(this::sizeWithoutFailure))
                .orElseThrow(() -> new IOException("yt-dlp did not produce the requested audio format"));
        }
    }

    public synchronized void deleteJobDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        requireInside(normalized, jobsRoot);
        if (normalized.equals(jobsRoot)) {
            throw new IOException("Refusing to delete the jobs root");
        }
        deleteTree(normalized);
    }

    public synchronized void cleanCache() throws IOException {
        if (!Files.isDirectory(cacheRoot)) {
            return;
        }

        List<CacheDirectory> retained = new ArrayList<>();
        Instant expirationThreshold = Instant.now().minus(properties.getCacheTtl());
        try (Stream<Path> directories = Files.list(cacheRoot)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                FileTime lastModified = Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS);
                if (lastModified.toInstant().isBefore(expirationThreshold)) {
                    deleteTree(directory);
                    continue;
                }
                retained.add(new CacheDirectory(directory, directorySize(directory), lastModified.toInstant()));
            }
        }

        long total = retained.stream().mapToLong(CacheDirectory::size).sum();
        retained.sort(Comparator.comparing(CacheDirectory::lastAccessed));
        for (CacheDirectory directory : retained) {
            if (total <= properties.cacheMaxBytes()) {
                break;
            }
            deleteTree(directory.path());
            total -= directory.size();
        }
    }

    public boolean isWritable() {
        return Files.isDirectory(root) && Files.isWritable(root);
    }

    private Path cacheDirectory(String cacheKey) throws IOException {
        if (cacheKey == null || !cacheKey.matches("[a-f0-9]{64}")) {
            throw new IOException("Invalid cache key");
        }
        Path directory = cacheRoot.resolve(cacheKey).normalize();
        requireInside(directory, cacheRoot);
        return directory;
    }

    private void writeMetadata(Path target, CacheMetadata metadata) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        objectMapper.writeValue(temporary.toFile(), metadata);
        moveReplacing(temporary, target);
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String sanitizedExtension(String format) throws IOException {
        String value = format == null ? "" : format.toLowerCase(java.util.Locale.ROOT);
        if (!value.matches("[a-z0-9]{2,5}")) {
            throw new IOException("Invalid audio format");
        }
        return value;
    }

    private MediaDetails withSize(MediaDetails details, long size) {
        return new MediaDetails(
            details.videoId(),
            details.title(),
            details.author(),
            details.durationMs(),
            details.thumbnail(),
            details.url(),
            details.format(),
            details.bitrate(),
            size
        );
    }

    private long directorySize(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .mapToLong(this::sizeWithoutFailure)
                .sum();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private long sizeWithoutFailure(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private void requireInside(Path candidate, Path parent) throws IOException {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedParent)) {
            throw new IOException("Storage path escapes its configured root");
        }
    }

    private void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record CacheMetadata(
        String fileName,
        MediaDetails details,
        Instant createdAt,
        Instant lastAccessedAt
    ) {
    }

    private record CacheDirectory(Path path, long size, Instant lastAccessed) {
    }
}
