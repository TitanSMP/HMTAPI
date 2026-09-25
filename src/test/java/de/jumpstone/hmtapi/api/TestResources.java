package de.jumpstone.hmtapi.api;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class TestResources {

    private TestResources() {
    }

    static YamlConfiguration configuration(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(new StringReader(yaml));
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalArgumentException("Invalid test configuration", e);
        }
        return configuration;
    }

    static String read(String resource) throws IOException {
        InputStream stream = Objects.requireNonNull(TestResources.class.getResourceAsStream(resource), resource);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
