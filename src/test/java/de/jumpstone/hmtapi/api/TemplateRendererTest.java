package de.jumpstone.hmtapi.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateRendererTest {

    @Test
    void replacesBuiltInPlaceholders() {
        String value = "{username} logged in at {last_login} and was last seen at {last_seen}";

        value = TemplateRenderer.applyUsername(value, "Notch");
        value = TemplateRenderer.applyLastLogin(value, "2026/09/26 10:00:00");
        value = TemplateRenderer.applyLastSeen(value, "2026/09/26 11:00:00");

        assertEquals("Notch logged in at 2026/09/26 10:00:00 and was last seen at 2026/09/26 11:00:00", value);
    }

    @Test
    void treatsReplacementLiterally() {
        String value = TemplateRenderer.applyUsername("player: {username}", "a$b\\c");

        assertEquals("player: a$b\\c", value);
    }

    @Test
    void resolvesEveryPlaceholderInATemplate() {
        String value = TemplateRenderer.applyPlaceholders("{papi:%server_name%} / {papi:%player_count%}", key -> "[" + key + "]");

        assertEquals("[%server_name%] / [%player_count%]", value);
    }

    @Test
    void treatsResolvedValuesAsLiterals() {
        String value = TemplateRenderer.applyPlaceholders("balance {papi:%vault_eco_balance%}", key -> "$1\\0");

        assertEquals("balance $1\\0", value);
    }

    @Test
    void leavesTemplatesWithoutPlaceholdersUntouched() {
        assertEquals("plain", TemplateRenderer.applyPlaceholders("plain", key -> key));
        assertEquals("{} {papi", TemplateRenderer.applyPlaceholders("{} {papi", key -> key));
    }

    @Test
    void resolvesEmptyPlaceholderNames() {
        assertEquals("resolved", TemplateRenderer.applyPlaceholders("{papi:}", key -> "resolved"));
    }
}
