package dev.zulu.media.process;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface CommandRunner {
    CommandResult run(List<String> command, Path workingDirectory, Duration timeout, int maxOutputBytes)
        throws IOException, InterruptedException;
}
