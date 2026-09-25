package de.jumpstone.hmtapi.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointRegistryTest {

    private static final Logger LOGGER = Logger.getLogger(EndpointRegistryTest.class.getName());

    @Test
    void readsEndpointsFromConfiguration() {
        EndpointRegistry registry = load("""
                endpoints:
                  users:
                    require_player: true
                    object:
                      username: "{username}"
                      balance: "{papi:%vault_eco_balance%}"
                  global:
                    require_player: false
                    object:
                      server_name: "{papi:%server_name%}"
                """);

        assertTrue(registry.isConfigured());
        assertEquals(List.of("users", "global"), List.copyOf(registry.names()));

        Endpoint users = registry.get("users");
        assertNotNull(users);
        assertTrue(users.requirePlayer());
        assertNull(users.error());
        assertEquals(List.of("username", "balance"), List.copyOf(users.values().keySet()));
        assertEquals("{username}", users.values().get("username"));

        Endpoint global = registry.get("global");
        assertNotNull(global);
        assertFalse(global.requirePlayer());
    }

    @Test
    void reportsAMissingEndpointsSection() {
        EndpointRegistry registry = load("port: 4567");

        assertFalse(registry.isConfigured());
        assertTrue(registry.names().isEmpty());
        assertNull(registry.get("users"));
    }

    @Test
    void reportsAMissingObjectSection() {
        EndpointRegistry registry = load("""
                endpoints:
                  broken:
                    require_player: true
                """);

        Endpoint broken = registry.get("broken");
        assertNotNull(broken);
        assertEquals("endpoints.broken.object not found.", broken.error());
    }

    @Test
    void skipsInvalidEndpointNames() {
        EndpointRegistry registry = load("""
                endpoints:
                  "with space":
                    object:
                      a: b
                  "..":
                    object:
                      a: b
                  "valid-name_1":
                    object:
                      a: b
                """);

        assertEquals(List.of("valid-name_1"), List.copyOf(registry.names()));
    }

    @Test
    void skipsEndpointsThatAreNotSections() {
        EndpointRegistry registry = load("""
                endpoints:
                  broken: "just a string"
                """);

        assertTrue(registry.names().isEmpty());
    }

    @Test
    void keepsAnEmptyObject() {
        EndpointRegistry registry = load("""
                endpoints:
                  empty:
                    object: {}
                """);

        Endpoint empty = registry.get("empty");
        assertNotNull(empty);
        assertTrue(empty.values().isEmpty());
    }

    @Test
    void nonStringValuesAreStringified() {
        EndpointRegistry registry = load("""
                endpoints:
                  numbers:
                    object:
                      count: 42
                      enabled: true
                """);

        Endpoint numbers = registry.get("numbers");
        assertNotNull(numbers);
        assertEquals("42", numbers.values().get("count"));
        assertEquals("true", numbers.values().get("enabled"));
    }

    @Test
    void shippedConfigurationLoads() throws IOException {
        EndpointRegistry registry = EndpointRegistry.load(
                TestResources.configuration(TestResources.read("/config.yml")), LOGGER);

        assertTrue(registry.isConfigured());
        assertFalse(registry.names().isEmpty());
        assertNotNull(registry.get("users"));
    }

    private static EndpointRegistry load(String yaml) {
        return EndpointRegistry.load(TestResources.configuration(yaml), LOGGER);
    }
}
