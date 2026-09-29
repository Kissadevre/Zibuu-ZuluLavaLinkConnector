package dev.zulu.media.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record SearchResponse(
    @JsonProperty("search_id") String searchId,
    @JsonProperty("expires_at") Instant expiresAt,
    List<SearchTrack> results
) {
}
