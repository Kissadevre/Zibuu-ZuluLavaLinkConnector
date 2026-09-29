package dev.zulu.media.storage;

import dev.zulu.media.download.MediaDetails;
import java.nio.file.Path;

public record StoredMedia(Path path, MediaDetails details) {
}
