package dev.zulu.media.search;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SearchTrack(
    String id,
    String title,
    String author,
    @JsonProperty("duration_ms") long durationMs,
    String thumbnail,
    String url,
    @JsonProperty("is_live") boolean live
) {
}
