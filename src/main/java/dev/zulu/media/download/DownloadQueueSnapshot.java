package dev.zulu.media.download;

public record DownloadQueueSnapshot(int active, int queued, int capacity) {
}
