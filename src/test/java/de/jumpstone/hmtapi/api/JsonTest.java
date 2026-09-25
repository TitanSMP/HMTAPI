package de.jumpstone.hmtapi.api;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTest {

    @Test
    void writesErrorObjects() {
        assertEquals("{\"error\":\"Endpoint 'x' not found.\"}", Json.error("Endpoint 'x' not found."));
    }

    @Test
    void keepsHtmlCharactersReadable() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("value", "a<b>&c");

        assertEquals("{\"value\":\"a<b>&c\"}", Json.write(payload));
    }

    @Test
    void keepsTheConfiguredOrder() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("z", "1");
        payload.put("a", "2");
        payload.put("m", "3");

        assertEquals("{\"z\":\"1\",\"a\":\"2\",\"m\":\"3\"}", Json.write(payload));
    }
}
