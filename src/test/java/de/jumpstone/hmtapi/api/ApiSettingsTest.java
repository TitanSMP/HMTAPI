package de.jumpstone.hmtapi.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiSettingsTest {

    private static final Logger LOGGER = Logger.getLogger(ApiSettingsTest.class.getName());

    @Test
    void defaultsAreUsedForAnEmptyConfiguration() {
        ApiSettings defaults = ApiSettings.defaults();
        ApiSettings loaded = load("");

        assertEquals(defaults.port(), loaded.port());
        assertEquals(defaults.bindAddress(), loaded.bindAddress());
        assertEquals(defaults.zone(), loaded.zone());
        assertEquals(defaults.dateFormat().toString(), loaded.dateFormat().toString());
        assertEquals(defaults.neverSeenValue(), loaded.neverSeenValue());
        assertEquals(defaults.requestTimeoutSeconds(), loaded.requestTimeoutSeconds());
    }

    @Test
    void readsValidValues() {
        ApiSettings loaded = load("""
                bind_address: "127.0.0.1"
                port: 8080
                request_timeout_seconds: 12
                time_zone: "Europe/Berlin"
                date_format: "dd.MM.yyyy HH:mm"
                never_seen_value: "-"
                """);

        assertEquals(8080, loaded.port());
        assertEquals("127.0.0.1", loaded.bindAddress());
        assertEquals(12, loaded.requestTimeoutSeconds());
        assertEquals(ZoneId.of("Europe/Berlin"), loaded.zone());
        assertEquals("26.09.2026 10:15", format(loaded, LocalDateTime.of(2026, 9, 26, 10, 15)));
        assertEquals("-", loaded.neverSeenValue());
        assertEquals("127.0.0.1:8080", loaded.address());
    }

    @Test
    void fallsBackWhenThePortIsOutOfRange() {
        assertEquals(ApiSettings.DEFAULT_PORT, load("port: 0").port());
        assertEquals(ApiSettings.DEFAULT_PORT, load("port: -1").port());
        assertEquals(ApiSettings.DEFAULT_PORT, load("port: 65536").port());
        assertEquals(ApiSettings.DEFAULT_PORT, load("port: 99999999").port());
        assertEquals(65535, load("port: 65535").port());
    }

    @Test
    void fallsBackWhenThePortIsNotANumber() {
        assertEquals(ApiSettings.DEFAULT_PORT, load("port: \"nope\"").port());
    }

    @Test
    void fallsBackForInvalidTimeouts() {
        assertEquals(ApiSettings.DEFAULT_REQUEST_TIMEOUT_SECONDS, load("request_timeout_seconds: 0").requestTimeoutSeconds());
        assertEquals(ApiSettings.DEFAULT_REQUEST_TIMEOUT_SECONDS, load("request_timeout_seconds: 61").requestTimeoutSeconds());
    }

    @Test
    void fallsBackForAnUnknownTimeZone() {
        assertEquals(ZoneId.of("UTC"), load("time_zone: \"Mars/Olympus\"").zone());
    }

    @Test
    void fallsBackForAnInvalidDateFormat() {
        ApiSettings loaded = load("date_format: \"yyyy-QQQQQQQ-dd\"");

        assertEquals(format(ApiSettings.defaults(), LocalDateTime.of(2026, 9, 26, 10, 15)),
                format(loaded, LocalDateTime.of(2026, 9, 26, 10, 15)));
    }

    @Test
    void fallsBackForAnEmptyBindAddress() {
        assertEquals(ApiSettings.DEFAULT_BIND_ADDRESS, load("bind_address: \"  \"").bindAddress());
    }

    @Test
    void keepsAnExplicitlyEmptyNeverSeenValue() {
        assertEquals("", load("never_seen_value: \"\"").neverSeenValue());
    }

    @Test
    void shippedConfigurationIsValid() throws IOException {
        ApiSettings loaded = load(TestResources.read("/config.yml"));

        assertTrue(loaded.port() >= 1 && loaded.port() <= 65535);
        assertFalse(loaded.bindAddress().isBlank());
    }

    private static ApiSettings load(String yaml) {
        return ApiSettings.load(TestResources.configuration(yaml), LOGGER);
    }

    private static String format(ApiSettings settings, LocalDateTime moment) {
        return settings.dateFormat().format(moment.atZone(settings.zone()));
    }
}
