package de.jumpstone.hmtapi.api;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable snapshot of a single configured endpoint.
 *
 * @param name         the endpoint name as used in the URL
 * @param requirePlayer whether a username has to be supplied in the URL
 * @param values       the configured keys and their raw templates, in configuration order
 * @param error        a configuration problem that has to be reported instead of the payload
 */
public record Endpoint(
        String name,
        boolean requirePlayer,
        Map<String, String> values,
        @Nullable String error
) {

    public Endpoint {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
