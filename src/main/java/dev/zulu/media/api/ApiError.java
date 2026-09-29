package dev.zulu.media.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ApiError(
    String code,
    String message,
    @JsonProperty("request_path") String requestPath,
    Instant timestamp
) {
}
