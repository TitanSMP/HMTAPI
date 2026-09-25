package de.jumpstone.hmtapi.api;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the built-in and PlaceholderAPI placeholders inside a configured endpoint value.
 * <p>
 * All methods are pure functions, which keeps the templating logic independent of Bukkit and
 * therefore testable.
 */
public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{papi:(.*?)}");

    private TemplateRenderer() {
    }

    public static String applyUsername(String template, String username) {
        return template.replace("{username}", username);
    }

    public static String applyLastLogin(String template, String lastLogin) {
        return template.replace("{last_login}", lastLogin);
    }

    public static String applyLastSeen(String template, String lastSeen) {
        return template.replace("{last_seen}", lastSeen);
    }

    public static String applyPlaceholders(String template, UnaryOperator<String> resolver) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        if (!matcher.find()) {
            return template;
        }
        StringBuilder rendered = new StringBuilder(template.length());
        do {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(resolver.apply(matcher.group(1))));
        } while (matcher.find());
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
