package dev.zulu.media.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.zulu.media.download.MediaDetails;
import dev.zulu.media.storage.StoredMedia;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.PathVariable;

final class DownloadControllerTest {

    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest
    @MethodSource("downloadEndpoints")
    void declaresTheJobIdPathVariableExplicitly(String methodName, Class<?>[] parameterTypes) throws NoSuchMethodException {
        Method method = DownloadController.class.getMethod(methodName, parameterTypes);
        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);

        assertEquals("jobId", pathVariable.value());
    }

    @Test
    void writesTheMediaFileDirectlyToTheServletResponse() throws Exception {
        byte[] audio = "test-audio".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path path = temporaryDirectory.resolve("track.m4a");
        Files.write(path, audio);
        MediaDetails details = new MediaDetails(
            "dQw4w9WgXcQ",
            "Track / title",
            "Artist",
            1_000L,
            null,
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "m4a",
            "128K",
            audio.length
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        DownloadController.writeFileResponse(new StoredMedia(path, details), response);

        assertEquals("audio/mp4", response.getContentType());
        assertEquals(audio.length, response.getContentLengthLong());
        assertEquals(
            "Track _ title.m4a",
            ContentDisposition.parse(response.getHeader("Content-Disposition")).getFilename()
        );
        assertArrayEquals(audio, response.getContentAsByteArray());
    }

    private static Stream<Arguments> downloadEndpoints() {
        return Stream.of(
            Arguments.of("show", new Class<?>[] {UUID.class}),
            Arguments.of("file", new Class<?>[] {UUID.class, HttpServletResponse.class}),
            Arguments.of("delete", new Class<?>[] {UUID.class})
        );
    }
}
