package dev.zulu.media.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class YouTubeVideoIdTest {

    @Test
    void acceptsOnlyCanonicalVideoIdentifiers() {
        assertTrue(YouTubeVideoId.isValid("dQw4w9WgXcQ"));
        assertTrue(YouTubeVideoId.isValid("abc_def-123"));
        assertFalse(YouTubeVideoId.isValid("https://youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(YouTubeVideoId.isValid("short"));
        assertFalse(YouTubeVideoId.isValid("bad;command"));
    }

    @Test
    void buildsTheOnlyDownloadUrlUsedByThePlugin() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            YouTubeVideoId.canonicalUrl("dQw4w9WgXcQ")
        );
        assertThrows(IllegalArgumentException.class, () -> YouTubeVideoId.canonicalUrl("invalid"));
    }
}
