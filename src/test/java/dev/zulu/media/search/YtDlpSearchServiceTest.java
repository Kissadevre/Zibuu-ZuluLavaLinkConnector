package dev.zulu.media.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zulu.media.api.ApiException;
import dev.zulu.media.config.ZuluMediaProperties;
import dev.zulu.media.process.CommandResult;
import dev.zulu.media.process.CommandRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class YtDlpSearchServiceTest {

    @Test
    void returnsNormalizedSearchResultsAndKeepsTheQueryAsOneArgument() {
        AtomicReference<List<String>> captured = new AtomicReference<>();
        CommandRunner runner = (command, directory, timeout, maximum) -> {
            captured.set(command);
            return new CommandResult(0, """
                {"entries":[
                  {"id":"dQw4w9WgXcQ","title":"Never Gonna Give You Up","uploader":"Rick Astley","duration":213.4,"thumbnail":"https://img.test/one.jpg","is_live":false},
                  {"id":"not-valid","title":"Ignored"}
                ]}
                """, "", false, false);
        };
        ZuluMediaProperties properties = new ZuluMediaProperties();
        properties.setSearchResultTtl(Duration.ofMinutes(5));
        YtDlpSearchService service = new YtDlpSearchService(properties, runner, new ObjectMapper());

        SearchResponse response = service.search("Rick Astley; echo unsafe", 5);

        assertEquals(1, response.results().size());
        SearchTrack track = response.results().get(0);
        assertEquals("dQw4w9WgXcQ", track.id());
        assertEquals(213_400L, track.durationMs());
        assertFalse(track.live());
        assertEquals("ytsearch5:Rick Astley; echo unsafe", captured.get().get(captured.get().size() - 1));
        assertFalse(captured.get().contains("sh"));
    }

    @Test
    void rejectsInvalidLimitsBeforeStartingAProcess() {
        CommandRunner runner = (command, directory, timeout, maximum) -> {
            throw new AssertionError("Process must not run");
        };
        ZuluMediaProperties properties = new ZuluMediaProperties();
        YtDlpSearchService service = new YtDlpSearchService(properties, runner, new ObjectMapper());

        ApiException exception = assertThrows(ApiException.class, () -> service.search("valid search", 99));

        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
        assertEquals("invalid_limit", exception.code());
    }

    @Test
    void reportsProcessTimeoutsAsGatewayTimeouts() {
        CommandRunner runner = (command, directory, timeout, maximum) ->
            new CommandResult(-1, "", "", true, false);
        YtDlpSearchService service = new YtDlpSearchService(
            new ZuluMediaProperties(),
            runner,
            new ObjectMapper()
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.search("valid search", null));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.status());
        assertTrue(exception.getMessage().contains("timed out"));
    }
}
