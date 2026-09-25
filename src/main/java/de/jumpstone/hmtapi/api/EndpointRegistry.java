package de.jumpstone.hmtapi.api;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Immutable snapshot of the configured endpoints.
 * <p>
 * The HTTP worker threads only ever read a published snapshot, so reloading the configuration can
 * never expose a half-written {@link FileConfiguration} to a running request.
 */
public final class EndpointRegistry {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final Map<String, Endpoint> endpoints;
    private final boolean configured;

    private EndpointRegistry(Map<String, Endpoint> endpoints, boolean configured) {
        this.endpoints = endpoints;
        this.configured = configured;
    }

    public static EndpointRegistry empty() {
        return new EndpointRegistry(Map.of(), false);
    }

    public static EndpointRegistry load(FileConfiguration config, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection("endpoints");
        if (section == null) {
            logger.warning("No 'endpoints' section found in config.yml, every request will return an error until it is added.");
            return empty();
        }

        Map<String, Endpoint> loaded = new LinkedHashMap<>();
        for (String name : section.getKeys(false)) {
            if (!VALID_NAME.matcher(name).matches()) {
                logger.warning(() -> "Ignoring endpoint '" + name + "': endpoint names may only contain letters, digits, '_' and '-'.");
                continue;
            }
            ConfigurationSection endpointSection = section.getConfigurationSection(name);
            if (endpointSection == null) {
                logger.warning(() -> "Ignoring endpoint '" + name + "': 'endpoints." + name + "' is not a section.");
                continue;
            }
            loaded.put(name, readEndpoint(name, endpointSection, logger));
        }
        return new EndpointRegistry(Collections.unmodifiableMap(loaded), true);
    }

    public Endpoint get(String name) {
        return endpoints.get(name);
    }

    public boolean isConfigured() {
        return configured;
    }

    public Set<String> names() {
        return endpoints.keySet();
    }

    private static Endpoint readEndpoint(String name, ConfigurationSection section, Logger logger) {
        boolean requirePlayer = section.getBoolean("require_player", false);
        ConfigurationSection object = section.getConfigurationSection("object");
        if (object == null) {
            String message = "endpoints." + name + ".object not found.";
            logger.warning(() -> "'" + message + "' Requests to /api/" + name + " will return that error.");
            return new Endpoint(name, requirePlayer, Map.of(), message);
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : object.getKeys(false)) {
            Object raw = object.get(key);
            values.put(key, raw == null ? "" : String.valueOf(raw));
        }
        if (values.isEmpty()) {
            logger.warning(() -> "Endpoint '" + name + "' has an empty 'object' section, /api/" + name + " will return an empty object.");
        }
        return new Endpoint(name, requirePlayer, values, null);
    }
}
