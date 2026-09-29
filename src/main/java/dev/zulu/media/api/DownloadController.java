package dev.zulu.media.api;

import dev.zulu.media.download.DownloadJobSnapshot;
import dev.zulu.media.download.DownloadService;
import dev.zulu.media.storage.StoredMedia;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plugins/zulu-media/v1/downloads")
public final class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @PostMapping
    public ResponseEntity<DownloadResponse> create(@RequestBody CreateDownloadRequest request) {
        DownloadJobSnapshot job = downloadService.create(request == null ? null : request.videoId());
        URI location = URI.create("/plugins/zulu-media/v1/downloads/" + job.id());
        return ResponseEntity.accepted()
            .location(location)
            .body(DownloadResponse.from(job));
    }

    @GetMapping("/{jobId}")
    public DownloadResponse show(@PathVariable("jobId") UUID jobId) {
        return DownloadResponse.from(downloadService.get(jobId));
    }

    @GetMapping("/{jobId}/file")
    public void file(@PathVariable("jobId") UUID jobId, HttpServletResponse response) throws IOException {
        writeFileResponse(downloadService.getFile(jobId), response);
    }

    static void writeFileResponse(StoredMedia media, HttpServletResponse response) throws IOException {
        String contentType = media.details().format().equalsIgnoreCase("mp3")
            ? "audio/mpeg"
            : "audio/mp4";
        String filename = safeFilename(media.details().title()) + "." + media.details().format();

        response.setContentType(contentType);
        response.setContentLengthLong(media.details().sizeBytes());
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString()
        );
        Files.copy(media.path(), response.getOutputStream());
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> delete(@PathVariable("jobId") UUID jobId) {
        downloadService.delete(jobId);
        return ResponseEntity.noContent().build();
    }

    private static String safeFilename(String title) {
        String safe = title == null ? "audio" : title
            .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_")
            .strip();
        if (safe.isBlank()) {
            return "audio";
        }
        return safe.substring(0, Math.min(safe.length(), 100));
    }
}
