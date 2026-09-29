package dev.zulu.media.search;

import java.util.regex.Pattern;

public final class YouTubeVideoId {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private YouTubeVideoId() {
    }

    public static boolean isValid(String videoId) {
        return videoId != null && PATTERN.matcher(videoId).matches();
    }

    public static String canonicalUrl(String videoId) {
        if (!isValid(videoId)) {
            throw new IllegalArgumentException("Invalid YouTube video ID");
        }
        return "https://www.youtube.com/watch?v=" + videoId;
    }
}
