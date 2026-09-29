package dev.zulu.media.process;

public record CommandResult(
    int exitCode,
    String standardOutput,
    String standardError,
    boolean timedOut,
    boolean outputTruncated
) {
    public boolean succeeded() {
        return !timedOut && !outputTruncated && exitCode == 0;
    }
}
