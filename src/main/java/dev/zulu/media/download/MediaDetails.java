package dev.zulu.media.download;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MediaDetails(
    @JsonProperty("video_id") String videoId,
    String title,
    String author,
    @JsonProperty("duration_ms") long durationMs,
    String thumbnail,
    String url,
    String format,
    String bitrate,
    @JsonProperty("size_bytes") long sizeBytes
) {
}
