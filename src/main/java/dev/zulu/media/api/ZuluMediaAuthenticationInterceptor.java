package dev.zulu.media.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zulu.media.config.ZuluMediaProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public final class ZuluMediaAuthenticationInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "X-Zulu-Media-Token";

    private final ZuluMediaProperties properties;
    private final ObjectMapper objectMapper;

    public ZuluMediaAuthenticationInterceptor(ZuluMediaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!properties.isEnabled()) {
            writeError(response, request, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "plugin_disabled", "Zulu Media is disabled");
            return false;
        }

        String expected = properties.getApiToken();
        if (expected.isBlank()) {
            writeError(response, request, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "token_not_configured", "Zulu Media API token is not configured");
            return false;
        }

        String supplied = request.getHeader(TOKEN_HEADER);
        if (supplied == null || supplied.isBlank()) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                supplied = authorization.substring(7).trim();
            }
        }

        if (supplied == null || !constantTimeEquals(expected, supplied)) {
            writeError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", "A valid Zulu Media API token is required");
            return false;
        }

        return true;
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request, int status, String code, String message)
        throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiError(code, message, request.getRequestURI(), Instant.now()));
    }
}
