package dev.zulu.media.api;

import dev.zulu.media.config.ZuluMediaProperties;
import dev.zulu.media.download.DownloadService;
import dev.zulu.media.process.CommandResult;
import dev.zulu.media.process.CommandRunner;
import dev.zulu.media.storage.FileMediaStorage;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plugins/zulu-media/v1")
public final class HealthController {

    private static final int HEALTH_OUTPUT_LIMIT = 64 * 1024;

    private final ZuluMediaProperties properties;
    private final CommandRunner commandRunner;
    private final FileMediaStorage storage;
    private final DownloadService downloadService;

    public HealthController(
        ZuluMediaProperties properties,
        CommandRunner commandRunner,
        FileMediaStorage storage,
        DownloadService downloadService
    ) {
        this.properties = properties;
        this.commandRunner = commandRunner;
        this.storage = storage;
        this.downloadService = downloadService;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        Map<String, HealthResponse.ComponentHealth> components = new LinkedHashMap<>();
        components.put("yt_dlp", executable(properties.getYtDlpPath(), "--version"));
        components.put("ffmpeg", executable(properties.getFfmpegPath(), "-version"));
        components.put(
            "storage",
            storage.isWritable()
                ? new HealthResponse.ComponentHealth("up", storage.root().toString())
                : new HealthResponse.ComponentHealth("down", "Storage path is not writable")
        );

        boolean healthy = components.values().stream().allMatch(component -> component.status().equals("up"));
        HealthResponse response = new HealthResponse(
            healthy ? "up" : "degraded",
            Map.copyOf(components),
            downloadService.queueSnapshot()
        );
        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    private HealthResponse.ComponentHealth executable(String executable, String argument) {
        try {
            CommandResult result = commandRunner.run(
                List.of(executable, argument),
                null,
                Duration.ofSeconds(5),
                HEALTH_OUTPUT_LIMIT
            );
            if (!result.succeeded()) {
                return new HealthResponse.ComponentHealth("down", "Executable returned a non-zero result");
            }
            String version = firstLine(result.standardOutput());
            if (version.isBlank()) {
                version = firstLine(result.standardError());
            }
            return new HealthResponse.ComponentHealth("up", version);
        } catch (IOException exception) {
            return new HealthResponse.ComponentHealth("down", "Executable was not found or could not run");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new HealthResponse.ComponentHealth("down", "Health check was interrupted");
        }
    }

    private String firstLine(String output) {
        if (output == null || output.isBlank()) {
            return "available";
        }
        return output.lines().findFirst().orElse("available").strip();
    }
}
