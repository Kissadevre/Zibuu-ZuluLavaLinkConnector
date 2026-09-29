package dev.zulu.media.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.zulu.media.download.DownloadQueueSnapshot;
import java.util.Map;

public record HealthResponse(
    String status,
    Map<String, ComponentHealth> components,
    @JsonProperty("download_queue") DownloadQueueSnapshot downloadQueue
) {
    public record ComponentHealth(String status, String detail) {
    }
}
