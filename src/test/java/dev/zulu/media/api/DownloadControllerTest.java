package dev.zulu.media.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.bind.annotation.PathVariable;

final class DownloadControllerTest {

    @ParameterizedTest
    @MethodSource("downloadEndpoints")
    void declaresTheJobIdPathVariableExplicitly(String methodName) throws NoSuchMethodException {
        Method method = DownloadController.class.getMethod(methodName, UUID.class);
        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);

        assertEquals("jobId", pathVariable.value());
    }

    private static Stream<String> downloadEndpoints() {
        return Stream.of("show", "file", "delete");
    }
}
