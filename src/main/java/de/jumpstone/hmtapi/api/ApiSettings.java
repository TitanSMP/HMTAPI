package de.jumpstone.hmtapi.api;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Immutable, validated view of the plugin configuration. Instances are created on the server
 * thread and afterwards only read by the HTTP worker threads.
 */
public record ApiSettings(
        int port,
        String bindAddress,
        ZoneId zone,
        DateTimeFormatter dateFormat,
        String neverSeenValue,
        int requestTimeoutSeconds
) {

    public static final int DEFAULT_PORT = 4567;
    public static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";
    public static final String DEFAULT_DATE_FORMAT = "yyyy/MM/dd HH:mm:ss";
    public static final String DEFAULT_TIME_ZONE = "UTC";
    public static final String DEFAULT_NEVER_SEEN_VALUE = "never";
    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 5;

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int MAX_TIMEOUT_SECONDS = 60;

    public static ApiSettings defaults() {
        return new ApiSettings(
                DEFAULT_PORT,
                DEFAULT_BIND_ADDRESS,
                ZoneId.of(DEFAULT_TIME_ZONE),
                DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT, Locale.ROOT),
                DEFAULT_NEVER_SEEN_VALUE,
                DEFAULT_REQUEST_TIMEOUT_SECONDS
        );
    }

    public static ApiSettings load(FileConfiguration config, Logger logger) {
        return new ApiSettings(
                readPort(config, logger),
                readBindAddress(config, logger),
                readZone(config, logger),
                readDateFormat(config, logger),
                readNeverSeenValue(config, logger),
                readRequestTimeout(config, logger)
        );
    }

    public String address() {
        return bindAddress + ":" + port;
    }

    private static int readPort(FileConfiguration config, Logger logger) {
        int port = config.getInt("port", DEFAULT_PORT);
        if (port < MIN_PORT || port > MAX_PORT) {
            logger.warning(() -> "Invalid port '" + port + "' in config.yml, it must be between "
                    + MIN_PORT + " and " + MAX_PORT + ". Falling back to " + DEFAULT_PORT + ".");
            return DEFAULT_PORT;
        }
        return port;
    }

    private static String readBindAddress(FileConfiguration config, Logger logger) {
        String bindAddress = config.getString("bind_address", DEFAULT_BIND_ADDRESS);
        if (bindAddress == null || bindAddress.isBlank()) {
            logger.warning("'bind_address' in config.yml is empty, falling back to " + DEFAULT_BIND_ADDRESS + ".");
            return DEFAULT_BIND_ADDRESS;
        }
        return bindAddress.trim();
    }

    private static ZoneId readZone(FileConfiguration config, Logger logger) {
        String zoneId = config.getString("time_zone", DEFAULT_TIME_ZONE);
        if (zoneId == null || zoneId.isBlank()) {
            logger.warning("'time_zone' in config.yml is empty, falling back to " + DEFAULT_TIME_ZONE + ".");
            return ZoneId.of(DEFAULT_TIME_ZONE);
        }
        try {
            return ZoneId.of(zoneId.trim());
        } catch (DateTimeException e) {
            logger.warning(() -> "Unknown time zone '" + zoneId + "' in config.yml, falling back to " + DEFAULT_TIME_ZONE + ".");
            return ZoneId.of(DEFAULT_TIME_ZONE);
        }
    }

    private static DateTimeFormatter readDateFormat(FileConfiguration config, Logger logger) {
        String pattern = config.getString("date_format", DEFAULT_DATE_FORMAT);
        if (pattern == null || pattern.isBlank()) {
            logger.warning("'date_format' in config.yml is empty, falling back to '" + DEFAULT_DATE_FORMAT + "'.");
            return DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT, Locale.ROOT);
        }
        try {
            return DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
        } catch (IllegalArgumentException e) {
            logger.warning(() -> "Invalid date format '" + pattern + "' in config.yml, falling back to '"
                    + DEFAULT_DATE_FORMAT + "'.");
            return DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT, Locale.ROOT);
        }
    }

    private static String readNeverSeenValue(FileConfiguration config, Logger logger) {
        String neverSeen = config.getString("never_seen_value", DEFAULT_NEVER_SEEN_VALUE);
        if (neverSeen == null) {
            logger.warning("'never_seen_value' in config.yml is not a string, falling back to '"
                    + DEFAULT_NEVER_SEEN_VALUE + "'.");
            return DEFAULT_NEVER_SEEN_VALUE;
        }
        return neverSeen;
    }

    private static int readRequestTimeout(FileConfiguration config, Logger logger) {
        int timeout = config.getInt("request_timeout_seconds", DEFAULT_REQUEST_TIMEOUT_SECONDS);
        if (timeout < 1 || timeout > MAX_TIMEOUT_SECONDS) {
            logger.warning(() -> "Invalid 'request_timeout_seconds' value '" + timeout + "' in config.yml, it must be between 1 and "
                    + MAX_TIMEOUT_SECONDS + ". Falling back to " + DEFAULT_REQUEST_TIMEOUT_SECONDS + ".");
            return DEFAULT_REQUEST_TIMEOUT_SECONDS;
        }
        return timeout;
    }
}
