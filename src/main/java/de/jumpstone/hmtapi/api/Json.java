package de.jumpstone.hmtapi.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Map;

/**
 * Serialises API responses. Gson instances are immutable and thread safe, so a single shared
 * instance is used for every request instead of allocating one per request.
 */
public final class Json {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {
    }

    public static String write(Object value) {
        return GSON.toJson(value);
    }

    public static String error(String message) {
        return GSON.toJson(Map.of("error", message));
    }
}
