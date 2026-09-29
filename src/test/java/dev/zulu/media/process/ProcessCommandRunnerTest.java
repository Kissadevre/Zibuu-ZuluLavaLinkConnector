package dev.zulu.media.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProcessCommandRunnerTest {

    private final ProcessCommandRunner runner = new ProcessCommandRunner();

    @AfterEach
    void closeRunner() {
        runner.close();
    }

    @Test
    void capturesStandardStreamsWithoutMergingThem() throws Exception {
        CommandResult result = runner.run(
            List.of("/bin/sh", "-c", "printf output; printf error >&2"),
            null,
            Duration.ofSeconds(2),
            1024
        );

        assertEquals(0, result.exitCode());
        assertEquals("output", result.standardOutput());
        assertEquals("error", result.standardError());
        assertFalse(result.outputTruncated());
    }

    @Test
    void marksOutputThatExceedsTheConfiguredLimit() throws Exception {
        CommandResult result = runner.run(
            List.of("/bin/sh", "-c", "printf 1234567890"),
            null,
            Duration.ofSeconds(2),
            5
        );

        assertEquals("12345", result.standardOutput());
        assertTrue(result.outputTruncated());
        assertFalse(result.succeeded());
    }
}
