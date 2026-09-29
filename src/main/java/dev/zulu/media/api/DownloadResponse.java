package dev.zulu.media.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.zulu.media.download.DownloadJobSnapshot;
import dev.zulu.media.download.DownloadState;
import dev.zulu.media.download.MediaDetails;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DownloadResponse(
    @JsonProperty("job_id") String jobId,
    @JsonProperty("video_id") String videoId,
    String state,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("expires_at") Instant expiresAt,
    MediaDetails media,
    @JsonProperty("file_url") String fileUrl,
    String error
) {
    public static DownloadResponse from(DownloadJobSnapshot job) {
        String fileUrl = job.state() == DownloadState.READY
            ? "/plugins/zulu-media/v1/downloads/" + job.id() + "/file"
            : null;
        return new DownloadResponse(
            job.id().toString(),
            job.videoId(),
            job.state().name().toLowerCase(java.util.Locale.ROOT),
            job.createdAt(),
            job.updatedAt(),
            job.expiresAt(),
            job.media() == null ? null : job.media().details(),
            fileUrl,
            job.error()
        );
    }
}
