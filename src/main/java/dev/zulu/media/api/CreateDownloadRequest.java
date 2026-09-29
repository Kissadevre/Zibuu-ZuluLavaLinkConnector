package dev.zulu.media.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateDownloadRequest(@JsonProperty("video_id") String videoId) {
}
