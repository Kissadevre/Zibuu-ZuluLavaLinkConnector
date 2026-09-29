package dev.zulu.media.download;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zulu.media.api.ApiException;
import dev.zulu.media.config.ZuluMediaProperties;
import dev.zulu.media.process.CommandResult;
import dev.zulu.media.process.CommandRunner;
import dev.zulu.media.search.YouTubeVideoId;
import dev.zulu.media.storage.FileMediaStorage;
import dev.zulu.media.storage.StoredMedia;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public final class DownloadService {

    private static final Logger log = LoggerFactory.getLogger(DownloadService.class);
    private static final int MAX_PROCESS_OUTPUT_BYTES = 4 * 1024 * 1024;

    private final ZuluMediaProperties properties;
    private final CommandRunner commandRunner;
    private final ObjectMapper objectMapper;
    private final FileMediaStorage storage;
    private final Map<UUID, DownloadJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, UUID> activeByCacheKey = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor downloadExecutor;
    private final ScheduledExecutorService cleanupExecutor;

    public DownloadService(
        ZuluMediaProperties properties,
        CommandRunner commandRunner,
        ObjectMapper objectMapper,
        FileMediaStorage storage
    ) {
        this.properties = properties;
        this.commandRunner = commandRunner;
        this.objectMapper = objectMapper;
        this.storage = storage;
        this.downloadExecutor = new ThreadPoolExecutor(
            Math.max(1, properties.getMaxConcurrentDownloads()),
            Math.max(1, properties.getMaxConcurrentDownloads()),
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(Math.max(1, properties.getMaxQueuedDownloads())),
            runnable -> {
                Thread thread = new Thread(runnable, "zulu-media-download");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
        );
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "zulu-media-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PostConstruct
    public void startCleanup() {
        long interval = Math.max(1_000L, properties.getCleanupInterval().toMillis());
        cleanupExecutor.scheduleWithFixedDelay(this::cleanExpiredData, interval, interval, TimeUnit.MILLISECONDS);
    }

    public synchronized DownloadJobSnapshot create(String videoId) {
        validateVideoId(videoId);
        validateFormat();
        String cacheKey = storage.cacheKey(videoId, properties.getAudioFormat(), properties.getAudioBitrate());

        UUID activeId = activeByCacheKey.get(cacheKey);
        if (activeId != null) {
            DownloadJob active = jobs.get(activeId);
            if (active != null && isActive(active.state())) {
                return active.snapshot(properties.getJobTtl());
            }
            activeByCacheKey.remove(cacheKey, activeId);
        }

        try {
            Optional<StoredMedia> cached = storage.findCached(cacheKey);
            if (cached.isPresent()) {
                DownloadJob job = new DownloadJob(UUID.randomUUID(), videoId, cacheKey);
                job.ready(cached.get());
                jobs.put(job.snapshot(properties.getJobTtl()).id(), job);
                return job.snapshot(properties.getJobTtl());
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "cache_unavailable", "The media cache is unavailable");
        }

        DownloadJob job = new DownloadJob(UUID.randomUUID(), videoId, cacheKey);
        UUID jobId = job.snapshot(properties.getJobTtl()).id();
        jobs.put(jobId, job);
        activeByCacheKey.put(cacheKey, jobId);

        try {
            downloadExecutor.execute(() -> execute(job));
        } catch (RejectedExecutionException exception) {
            jobs.remove(jobId);
            activeByCacheKey.remove(cacheKey, jobId);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "download_queue_full", "The download queue is full");
        }

        return job.snapshot(properties.getJobTtl());
    }

    public DownloadJobSnapshot get(UUID jobId) {
        DownloadJob job = jobs.get(jobId);
        if (job == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "download_not_found", "Download job was not found");
        }
        return job.snapshot(properties.getJobTtl());
    }

    public StoredMedia getFile(UUID jobId) {
        DownloadJobSnapshot job = get(jobId);
        if (job.state() == DownloadState.EXPIRED) {
            throw new ApiException(HttpStatus.GONE, "download_expired", "Download job has expired");
        }
        if (job.state() != DownloadState.READY || job.media() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "download_not_ready", "Download file is not ready");
        }
        if (!Files.isRegularFile(job.media().path())) {
            throw new ApiException(HttpStatus.GONE, "download_file_missing", "Download file is no longer available");
        }
        return job.media();
    }

    public synchronized void delete(UUID jobId) {
        DownloadJob job = jobs.get(jobId);
        if (job == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "download_not_found", "Download job was not found");
        }
        if (isActive(job.state())) {
            throw new ApiException(HttpStatus.CONFLICT, "download_in_progress", "An active download cannot be deleted");
        }
        job.transition(DownloadState.EXPIRED);
    }

    public DownloadQueueSnapshot queueSnapshot() {
        return new DownloadQueueSnapshot(
            downloadExecutor.getActiveCount(),
            downloadExecutor.getQueue().size(),
            properties.getMaxQueuedDownloads()
        );
    }

    private void execute(DownloadJob job) {
        Path jobDirectory = null;
        try {
            DownloadJobSnapshot snapshot = job.snapshot(properties.getJobTtl());
            jobDirectory = storage.createJobDirectory(snapshot.id());
            job.transition(DownloadState.DOWNLOADING);

            CommandResult result = commandRunner.run(
                downloadCommand(snapshot.videoId(), jobDirectory),
                null,
                properties.getDownloadTimeout(),
                MAX_PROCESS_OUTPUT_BYTES
            );
            ensureDownloadSucceeded(result);

            job.transition(DownloadState.PROCESSING);
            MediaDetails details = parseDetails(result.standardOutput(), snapshot.videoId());
            Path audio = storage.findDownloadedAudio(jobDirectory, properties.getAudioFormat());
            long size = Files.size(audio);
            validateDownloadedMedia(details, size);

            MediaDetails sizedDetails = new MediaDetails(
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
            StoredMedia stored = storage.store(job.cacheKey(), audio, sizedDetails);
            job.ready(stored);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            job.fail("Download was interrupted");
        } catch (Exception exception) {
            log.warn("Zulu Media download failed for job {}: {}", job.snapshot(properties.getJobTtl()).id(), exception.getMessage());
            job.fail(safeFailure(exception));
        } finally {
            DownloadJobSnapshot snapshot = job.snapshot(properties.getJobTtl());
            activeByCacheKey.remove(job.cacheKey(), snapshot.id());
            if (jobDirectory != null) {
                try {
                    storage.deleteJobDirectory(jobDirectory);
                } catch (IOException exception) {
                    log.warn("Unable to clean Zulu Media job directory {}", jobDirectory, exception);
                }
            }
        }
    }

    private List<String> downloadCommand(String videoId, Path jobDirectory) {
        long maximumDurationSeconds = Math.multiplyExact((long) properties.getMaxDurationMinutes(), 60L);
        List<String> command = new ArrayList<>(List.of(
            properties.getYtDlpPath(),
            "--ignore-config",
            "--no-color",
            "--no-warnings",
            "--quiet",
            "--no-progress",
            "--no-playlist",
            "--match-filter",
            "!is_live & duration <=? " + maximumDurationSeconds,
            "--format",
            "bestaudio/best",
            "--extract-audio",
            "--audio-format",
            properties.getAudioFormat(),
            "--audio-quality",
            properties.getAudioBitrate(),
            "--ffmpeg-location",
            properties.getFfmpegPath(),
            "--max-filesize",
            properties.getMaxFileMb() + "M",
            "--embed-metadata",
            "--paths",
            "home:" + jobDirectory,
            "--paths",
            "temp:" + jobDirectory,
            "--output",
            "%(id)s.%(ext)s",
            "--print",
            "after_move:%()j",
            YouTubeVideoId.canonicalUrl(videoId)
        ));
        return List.copyOf(command);
    }

    private void ensureDownloadSucceeded(CommandResult result) throws IOException {
        if (result.timedOut()) {
            throw new IOException("Download timed out");
        }
        if (result.outputTruncated()) {
            throw new IOException("yt-dlp returned too much output");
        }
        if (result.exitCode() != 0) {
            String error = result.standardError() == null ? "" : result.standardError().strip();
            throw new IOException(error.isBlank() ? "yt-dlp failed to download the media" : error);
        }
    }

    private MediaDetails parseDetails(String output, String expectedVideoId) throws IOException {
        JsonNode metadata = null;
        String[] lines = output == null ? new String[0] : output.lines().toArray(String[]::new);
        for (int index = lines.length - 1; index >= 0; index--) {
            String candidate = lines[index].strip();
            if (!candidate.startsWith("{")) {
                continue;
            }
            try {
                metadata = objectMapper.readTree(candidate);
                break;
            } catch (IOException ignored) {
                // Keep looking for the final structured yt-dlp line.
            }
        }
        if (metadata == null) {
            throw new IOException("yt-dlp did not return download metadata");
        }

        String videoId = metadata.path("id").asText("");
        if (!expectedVideoId.equals(videoId)) {
            throw new IOException("Downloaded media ID does not match the requested video");
        }
        boolean live = metadata.path("is_live").asBoolean(false)
            || "is_live".equals(metadata.path("live_status").asText());
        if (live) {
            throw new IOException("Live streams cannot be downloaded");
        }

        long durationMs = metadata.path("duration").isNumber()
            ? Math.max(0L, Math.round(metadata.path("duration").asDouble() * 1000D))
            : 0L;
        String title = textOr(metadata, "title", "Unknown title");
        String author = textOr(metadata, "uploader", textOr(metadata, "channel", "Unknown artist"));
        String thumbnail = metadata.path("thumbnail").isTextual() ? metadata.path("thumbnail").asText() : null;

        return new MediaDetails(
            videoId,
            title,
            author,
            durationMs,
            thumbnail,
            YouTubeVideoId.canonicalUrl(videoId),
            properties.getAudioFormat().toLowerCase(Locale.ROOT),
            properties.getAudioBitrate(),
            0L
        );
    }

    private void validateDownloadedMedia(MediaDetails details, long size) throws IOException {
        long maximumDurationMs = Math.multiplyExact((long) properties.getMaxDurationMinutes(), 60_000L);
        if (details.durationMs() <= 0L || details.durationMs() > maximumDurationMs) {
            throw new IOException("Media duration is missing or exceeds the configured limit");
        }
        if (size <= 0L || size > properties.maxFileBytes()) {
            throw new IOException("Downloaded audio exceeds the configured file size limit");
        }
    }

    private void validateVideoId(String videoId) {
        if (!YouTubeVideoId.isValid(videoId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_video_id", "A valid 11-character YouTube video ID is required");
        }
    }

    private void validateFormat() {
        String format = properties.getAudioFormat().toLowerCase(Locale.ROOT);
        if (!format.equals("m4a") && !format.equals("mp3")) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "invalid_audio_format", "Configured audio format must be m4a or mp3");
        }
    }

    private boolean isActive(DownloadState state) {
        return state == DownloadState.QUEUED
            || state == DownloadState.DOWNLOADING
            || state == DownloadState.PROCESSING;
    }

    private String textOr(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    private String safeFailure(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Download failed";
        }
        String singleLine = message.replace('\n', ' ').replace('\r', ' ').strip();
        return singleLine.substring(0, Math.min(singleLine.length(), 800));
    }

    private void cleanExpiredData() {
        try {
            Instant now = Instant.now();
            for (Map.Entry<UUID, DownloadJob> entry : jobs.entrySet()) {
                DownloadJob job = entry.getValue();
                if (isActive(job.state())) {
                    continue;
                }
                if (job.state() == DownloadState.EXPIRED) {
                    if (job.updatedAt().plus(properties.getJobTtl()).isBefore(now)) {
                        jobs.remove(entry.getKey(), job);
                    }
                } else if (job.updatedAt().plus(properties.getJobTtl()).isBefore(now)) {
                    job.transition(DownloadState.EXPIRED);
                }
            }
            storage.cleanCache();
        } catch (Exception exception) {
            log.warn("Zulu Media cleanup failed", exception);
        }
    }

    @PreDestroy
    public void close() {
        cleanupExecutor.shutdownNow();
        downloadExecutor.shutdownNow();
    }
}
