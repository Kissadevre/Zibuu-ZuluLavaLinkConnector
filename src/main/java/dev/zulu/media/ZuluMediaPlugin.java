package dev.zulu.media;

import dev.arbjerg.lavalink.api.PluginEventHandler;
import dev.zulu.media.config.ZuluMediaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(ZuluMediaProperties.class)
public final class ZuluMediaPlugin extends PluginEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ZuluMediaPlugin.class);

    public ZuluMediaPlugin(ZuluMediaProperties properties) {
        log.info(
            "Zulu Media plugin initialized (enabled={}, storage={})",
            properties.isEnabled(),
            properties.getStoragePath()
        );

        if (properties.isEnabled() && properties.getApiToken().isBlank()) {
            log.warn("Zulu Media API is enabled without an API token; requests will be rejected until one is configured");
        }
    }
}
