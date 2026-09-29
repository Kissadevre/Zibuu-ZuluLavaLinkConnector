package dev.zulu.media.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zulu.media.api.ApiException;
import dev.zulu.media.config.ZuluMediaProperties;
import dev.zulu.media.process.CommandResult;
import dev.zulu.media.process.CommandRunner;
import dev.zulu.media.storage.FileMediaStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

class DownloadServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void downloadsOnceAndThenServesThePersistentCache() throws Exception {
        ZuluMediaProperties properties = properties();
        AtomicInteger executions = new AtomicInteger();
        AtomicReference<List<String>> command = new AtomicReference<>();
        CommandRunner runner = (arguments, workingDirectory, timeout, maximum) -> {
            executions.incrementAndGet();
            command.set(arguments);
            Path outputDirectory = arguments.stream()
                .filter(argument -> argument.startsWith("home:"))
                .map(argument -> Path.of(argument.substring("home:".length())))
                .findFirst()
                .orElseThrow();
            Files.writeString(outputDirectory.resolve("dQw4w9WgXcQ.m4a"), "audio-bytes");
            return new CommandResult(0, """
                {"id":"dQw4w9WgXcQ","title":"Never Gonna Give You Up","uploader":"Rick Astley","duration":213,"thumbnail":"https://img.test/one.jpg","is_live":false}
                """, "", false, false);
        };
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        FileMediaStorage storage = new FileMediaStorage(properties, mapper);
        storage.initialize();
        DownloadService service = new DownloadService(properties, runner, mapper, storage);

        try {
            DownloadJobSnapshot initial = service.create("dQw4w9WgXcQ");
            DownloadJobSnapshot completed = awaitTerminal(service, initial);

            assertEquals(DownloadState.READY, completed.state());
            assertNotNull(completed.media());
            assertEquals(11L, completed.media().details().sizeBytes());
            assertTrue(Files.isRegularFile(completed.media().path()));
            assertEquals(1, executions.get());
            assertFalse(command.get().contains("sh"));
            assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", command.get().get(command.get().size() - 1));

            DownloadJobSnapshot cached = service.create("dQw4w9WgXcQ");
            assertEquals(DownloadState.READY, cached.state());
            assertEquals(1, executions.get());
        } finally {
            service.close();
        }
    }

    @Test
    void rejectsAnythingOtherThanAYouTubeVideoId() throws Exception {
        ZuluMediaProperties properties = properties();
        CommandRunner runner = (arguments, workingDirectory, timeout, maximum) -> {
            throw new AssertionError("Process must not run");
        };
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        FileMediaStorage storage = new FileMediaStorage(properties, mapper);
        storage.initialize();
        DownloadService service = new DownloadService(properties, runner, mapper, storage);

        try {
            ApiException exception = assertThrows(
                ApiException.class,
                () -> service.create("https://example.com/file.mp3")
            );
            assertEquals(HttpStatus.BAD_REQUEST, exception.status());
            assertEquals("invalid_video_id", exception.code());
        } finally {
            service.close();
        }
    }

    private ZuluMediaProperties properties() {
        ZuluMediaProperties properties = new ZuluMediaProperties();
        properties.setStoragePath(temporaryDirectory.resolve("media"));
        properties.setJobTtl(Duration.ofMinutes(5));
        properties.setCacheTtl(Duration.ofMinutes(5));
        properties.setMaxFileMb(1);
        return properties;
    }

    private DownloadJobSnapshot awaitTerminal(DownloadService service, DownloadJobSnapshot initial) throws Exception {
        DownloadJobSnapshot current = initial;
        for (int attempt = 0; attempt < 100; attempt++) {
            current = service.get(initial.id());
            if (current.state() == DownloadState.READY || current.state() == DownloadState.FAILED) {
                return current;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Download did not finish: " + current.state());
    }
}
