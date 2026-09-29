package dev.zulu.media.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zulu.media.api.ApiException;
import dev.zulu.media.config.ZuluMediaProperties;
import dev.zulu.media.process.CommandResult;
import dev.zulu.media.process.CommandRunner;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public final class YtDlpSearchService {

    private static final int MAX_PROCESS_OUTPUT_BYTES = 2 * 1024 * 1024;

    private final ZuluMediaProperties properties;
    private final CommandRunner commandRunner;
    private final ObjectMapper objectMapper;
    private final Semaphore concurrency;

    public YtDlpSearchService(
        ZuluMediaProperties properties,
        CommandRunner commandRunner,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.commandRunner = commandRunner;
        this.objectMapper = objectMapper;
        this.concurrency = new Semaphore(Math.max(1, properties.getMaxConcurrentSearches()));
    }

    public SearchResponse search(String rawQuery, Integer requestedLimit) {
        String query = validateQuery(rawQuery);
        int limit = validateLimit(requestedLimit);

        if (!concurrency.tryAcquire()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "search_busy", "Too many searches are already running");
        }

        try {
            CommandResult result = commandRunner.run(
                command(query, limit),
                null,
                properties.getSearchTimeout(),
                MAX_PROCESS_OUTPUT_BYTES
            );
            ensureSuccess(result);
            return parse(result.standardOutput(), limit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "search_interrupted", "The search was interrupted");
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "search_unavailable", "yt-dlp could not be executed");
        } finally {
            concurrency.release();
        }
    }

    private List<String> command(String query, int limit) {
        return List.of(
            properties.getYtDlpPath(),
            "--ignore-config",
            "--no-color",
            "--no-warnings",
            "--quiet",
            "--flat-playlist",
            "--dump-single-json",
            "--playlist-end",
            Integer.toString(limit),
            "ytsearch" + limit + ":" + query
        );
    }

    private void ensureSuccess(CommandResult result) {
        if (result.timedOut()) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "search_timeout", "The YouTube search timed out");
        }
        if (result.outputTruncated()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "search_output_too_large", "yt-dlp returned too much data");
        }
        if (result.exitCode() != 0) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "search_failed", safeProcessMessage(result.standardError()));
        }
    }

    private SearchResponse parse(String json, int limit) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode entries = root.path("entries");
            if (!entries.isArray()) {
                throw new IOException("Search response did not contain entries");
            }

            List<SearchTrack> tracks = new ArrayList<>();
            for (JsonNode entry : entries) {
                SearchTrack track = toTrack(entry);
                if (track != null) {
                    tracks.add(track);
                }
                if (tracks.size() == limit) {
                    break;
                }
            }

            return new SearchResponse(
                UUID.randomUUID().toString(),
                Instant.now().plus(properties.getSearchResultTtl()),
                List.copyOf(tracks)
            );
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "invalid_search_response", "yt-dlp returned an invalid search response");
        }
    }

    private SearchTrack toTrack(JsonNode entry) {
        String id = text(entry, "id");
        if (!YouTubeVideoId.isValid(id)) {
            return null;
        }

        String title = fallback(text(entry, "title"), "Unknown title");
        String author = fallback(text(entry, "uploader"), text(entry, "channel"));
        author = fallback(author, "Unknown artist");
        long durationMs = entry.path("duration").isNumber()
            ? Math.max(0L, Math.round(entry.path("duration").asDouble() * 1000D))
            : 0L;
        boolean live = entry.path("is_live").asBoolean(false)
            || "is_live".equals(entry.path("live_status").asText());
        String thumbnail = text(entry, "thumbnail");

        return new SearchTrack(
            id,
            title,
            author,
            durationMs,
            thumbnail,
            YouTubeVideoId.canonicalUrl(id),
            live
        );
    }

    private String validateQuery(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 2 || query.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_query", "Search query must contain between 2 and 200 characters");
        }
        if (query.indexOf('\0') >= 0 || query.contains("\n") || query.contains("\r")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_query", "Search query contains unsupported characters");
        }
        return query;
    }

    private int validateLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? properties.getDefaultSearchLimit() : requestedLimit;
        if (limit < 1 || limit > properties.getMaxSearchLimit()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "invalid_limit",
                "Search limit must be between 1 and " + properties.getMaxSearchLimit()
            );
        }
        return limit;
    }

    private String safeProcessMessage(String standardError) {
        String message = standardError == null ? "" : standardError.strip();
        if (message.isEmpty()) {
            return "yt-dlp could not complete the search";
        }
        return message.substring(0, Math.min(message.length(), 500));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
