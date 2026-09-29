package dev.zulu.media.config;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plugins.zulu-media")
public class ZuluMediaProperties {

    private boolean enabled = true;
    private String apiToken = "";
    private String ytDlpPath = "yt-dlp";
    private String ffmpegPath = "ffmpeg";
    private Path storagePath = Path.of("data", "zulu-media");
    private int defaultSearchLimit = 5;
    private int maxSearchLimit = 10;
    private int maxConcurrentSearches = 4;
    private int maxConcurrentDownloads = 2;
    private int maxQueuedDownloads = 20;
    private int maxDurationMinutes = 20;
    private int maxFileMb = 48;
    private String audioFormat = "m4a";
    private String audioBitrate = "128K";
    private Duration searchTimeout = Duration.ofSeconds(30);
    private Duration downloadTimeout = Duration.ofMinutes(10);
    private Duration searchResultTtl = Duration.ofMinutes(10);
    private Duration jobTtl = Duration.ofHours(1);
    private Duration cacheTtl = Duration.ofHours(24);
    private int cacheMaxMb = 2048;
    private Duration cleanupInterval = Duration.ofMinutes(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiToken() {
        return apiToken == null ? "" : apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getYtDlpPath() {
        return ytDlpPath;
    }

    public void setYtDlpPath(String ytDlpPath) {
        this.ytDlpPath = ytDlpPath;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public Path getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(Path storagePath) {
        this.storagePath = storagePath;
    }

    public int getDefaultSearchLimit() {
        return defaultSearchLimit;
    }

    public void setDefaultSearchLimit(int defaultSearchLimit) {
        this.defaultSearchLimit = defaultSearchLimit;
    }

    public int getMaxSearchLimit() {
        return maxSearchLimit;
    }

    public void setMaxSearchLimit(int maxSearchLimit) {
        this.maxSearchLimit = maxSearchLimit;
    }

    public int getMaxConcurrentSearches() {
        return maxConcurrentSearches;
    }

    public void setMaxConcurrentSearches(int maxConcurrentSearches) {
        this.maxConcurrentSearches = maxConcurrentSearches;
    }

    public int getMaxConcurrentDownloads() {
        return maxConcurrentDownloads;
    }

    public void setMaxConcurrentDownloads(int maxConcurrentDownloads) {
        this.maxConcurrentDownloads = maxConcurrentDownloads;
    }

    public int getMaxQueuedDownloads() {
        return maxQueuedDownloads;
    }

    public void setMaxQueuedDownloads(int maxQueuedDownloads) {
        this.maxQueuedDownloads = maxQueuedDownloads;
    }

    public int getMaxDurationMinutes() {
        return maxDurationMinutes;
    }

    public void setMaxDurationMinutes(int maxDurationMinutes) {
        this.maxDurationMinutes = maxDurationMinutes;
    }

    public int getMaxFileMb() {
        return maxFileMb;
    }

    public void setMaxFileMb(int maxFileMb) {
        this.maxFileMb = maxFileMb;
    }

    public String getAudioFormat() {
        return audioFormat;
    }

    public void setAudioFormat(String audioFormat) {
        this.audioFormat = audioFormat;
    }

    public String getAudioBitrate() {
        return audioBitrate;
    }

    public void setAudioBitrate(String audioBitrate) {
        this.audioBitrate = audioBitrate;
    }

    public Duration getSearchTimeout() {
        return searchTimeout;
    }

    public void setSearchTimeout(Duration searchTimeout) {
        this.searchTimeout = searchTimeout;
    }

    public Duration getDownloadTimeout() {
        return downloadTimeout;
    }

    public void setDownloadTimeout(Duration downloadTimeout) {
        this.downloadTimeout = downloadTimeout;
    }

    public Duration getSearchResultTtl() {
        return searchResultTtl;
    }

    public void setSearchResultTtl(Duration searchResultTtl) {
        this.searchResultTtl = searchResultTtl;
    }

    public Duration getJobTtl() {
        return jobTtl;
    }

    public void setJobTtl(Duration jobTtl) {
        this.jobTtl = jobTtl;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public int getCacheMaxMb() {
        return cacheMaxMb;
    }

    public void setCacheMaxMb(int cacheMaxMb) {
        this.cacheMaxMb = cacheMaxMb;
    }

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    public long maxFileBytes() {
        return Math.multiplyExact((long) maxFileMb, 1024L * 1024L);
    }

    public long cacheMaxBytes() {
        return Math.multiplyExact((long) cacheMaxMb, 1024L * 1024L);
    }

    @PostConstruct
    public void validate() {
        require(ytDlpPath != null && !ytDlpPath.isBlank(), "yt-dlp-path must not be blank");
        require(ffmpegPath != null && !ffmpegPath.isBlank(), "ffmpeg-path must not be blank");
        require(storagePath != null, "storage-path must not be null");
        require(defaultSearchLimit > 0, "default-search-limit must be positive");
        require(maxSearchLimit >= defaultSearchLimit, "max-search-limit must be at least default-search-limit");
        require(maxConcurrentSearches > 0, "max-concurrent-searches must be positive");
        require(maxConcurrentDownloads > 0, "max-concurrent-downloads must be positive");
        require(maxQueuedDownloads > 0, "max-queued-downloads must be positive");
        require(maxDurationMinutes > 0, "max-duration-minutes must be positive");
        require(maxFileMb > 0, "max-file-mb must be positive");
        require(cacheMaxMb > 0, "cache-max-mb must be positive");
        require(audioFormat != null && List.of("m4a", "mp3").contains(audioFormat.toLowerCase(Locale.ROOT)),
            "audio-format must be m4a or mp3");
        require(audioBitrate != null && audioBitrate.matches("(?i)^[0-9]{2,4}K$"),
            "audio-bitrate must be a bitrate such as 128K");
        requirePositive(searchTimeout, "search-timeout");
        requirePositive(downloadTimeout, "download-timeout");
        requirePositive(searchResultTtl, "search-result-ttl");
        requirePositive(jobTtl, "job-ttl");
        requirePositive(cacheTtl, "cache-ttl");
        requirePositive(cleanupInterval, "cleanup-interval");
    }

    private void requirePositive(Duration duration, String name) {
        require(duration != null && !duration.isZero() && !duration.isNegative(), name + " must be positive");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Invalid plugins.zulu-media configuration: " + message);
        }
    }
}
