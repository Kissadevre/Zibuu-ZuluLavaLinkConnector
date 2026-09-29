package dev.zulu.media.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ZuluMediaPropertiesTest {

    @Test
    void defaultConfigurationIsValid() {
        assertDoesNotThrow(() -> new ZuluMediaProperties().validate());
    }

    @Test
    void rejectsUnsafeOrNonsensicalConfiguration() {
        ZuluMediaProperties properties = new ZuluMediaProperties();
        properties.setAudioFormat("exe");

        assertThrows(IllegalStateException.class, properties::validate);
    }
}
