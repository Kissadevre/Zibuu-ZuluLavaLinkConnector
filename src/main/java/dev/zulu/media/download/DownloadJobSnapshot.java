package dev.zulu.media.download;

import dev.zulu.media.storage.StoredMedia;
import java.time.Instant;
import java.util.UUID;

public record DownloadJobSnapshot(
    UUID id,
    String videoId,
    DownloadState state,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt,
    StoredMedia media,
    String error
) {
}
