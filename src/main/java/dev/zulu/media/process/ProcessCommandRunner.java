package dev.zulu.media.process;

import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public final class ProcessCommandRunner implements CommandRunner {

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "zulu-media-process-stream");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public CommandResult run(List<String> command, Path workingDirectory, Duration timeout, int maxOutputBytes)
        throws IOException, InterruptedException {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Command must not be empty");
        }
        if (maxOutputBytes < 1) {
            throw new IllegalArgumentException("Maximum output size must be positive");
        }

        ProcessBuilder builder = new ProcessBuilder(List.copyOf(command));
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }

        Process process = builder.start();
        LimitedCollector stdout = new LimitedCollector(process.getInputStream(), maxOutputBytes);
        LimitedCollector stderr = new LimitedCollector(process.getErrorStream(), maxOutputBytes);
        Future<String> stdoutFuture = streamExecutor.submit(stdout);
        Future<String> stderrFuture = streamExecutor.submit(stderr);

        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        }

        int exitCode = completed ? process.exitValue() : -1;
        String standardOutput = await(stdoutFuture);
        String standardError = await(stderrFuture);

        return new CommandResult(
            exitCode,
            standardOutput,
            standardError,
            !completed,
            stdout.truncated() || stderr.truncated()
        );
    }

    private String await(Future<String> future) throws IOException, InterruptedException {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Unable to collect process output", cause);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new IOException("Timed out while collecting process output", exception);
        }
    }

    @PreDestroy
    public void close() {
        streamExecutor.shutdownNow();
    }

    private static final class LimitedCollector implements Callable<String> {
        private final InputStream input;
        private final int maximumBytes;
        private final AtomicInteger discarded = new AtomicInteger();

        private LimitedCollector(InputStream input, int maximumBytes) {
            this.input = input;
            this.maximumBytes = maximumBytes;
        }

        @Override
        public String call() throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                int writable = Math.min(read, Math.max(0, maximumBytes - output.size()));
                if (writable > 0) {
                    output.write(buffer, 0, writable);
                }
                if (writable < read) {
                    discarded.addAndGet(read - writable);
                }
            }
            return output.toString(StandardCharsets.UTF_8);
        }

        private boolean truncated() {
            return discarded.get() > 0;
        }
    }
}
