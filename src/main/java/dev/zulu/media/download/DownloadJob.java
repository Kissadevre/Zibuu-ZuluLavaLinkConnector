package dev.zulu.media.download;

import dev.zulu.media.storage.StoredMedia;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

final class DownloadJob {

    private final UUID id;
    private final String videoId;
    private final String cacheKey;
    private final Instant createdAt;
    private DownloadState state;
    private Instant updatedAt;
    private StoredMedia media;
    private String error;

    DownloadJob(UUID id, String videoId, String cacheKey) {
        this.id = id;
        this.videoId = videoId;
        this.cacheKey = cacheKey;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.state = DownloadState.QUEUED;
    }

    String cacheKey() {
        return cacheKey;
    }

    synchronized DownloadState state() {
        return state;
    }

    synchronized Instant updatedAt() {
        return updatedAt;
    }

    synchronized void transition(DownloadState next) {
        this.state = next;
        this.updatedAt = Instant.now();
    }

    synchronized void ready(StoredMedia storedMedia) {
        this.media = storedMedia;
        this.error = null;
        transition(DownloadState.READY);
    }

    synchronized void fail(String failure) {
        this.error = failure;
        transition(DownloadState.FAILED);
    }

    synchronized DownloadJobSnapshot snapshot(Duration ttl) {
        return new DownloadJobSnapshot(
            id,
            videoId,
            state,
            createdAt,
            updatedAt,
            updatedAt.plus(ttl),
            media,
            error
        );
    }
}
