package de.jumpstone.hmtapi.api;

import org.jetbrains.annotations.Nullable;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The outcome of matching a raw request path against the routes served by HMTAPI.
 *
 * @param kind     what the request addressed
 * @param endpoint the requested endpoint name, {@code null} unless {@link Kind#ENDPOINT}
 * @param username the requested player name, {@code null} when the route did not carry one
 */
public record RequestTarget(Kind kind, @Nullable String endpoint, @Nullable String username) {

    private static final String API_ROOT = "api";
    private static final String FAVICON_PATH = "/favicon.ico";
    private static final Pattern VALID_ENDPOINT = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final int MAX_USERNAME_LENGTH = 32;

    public enum Kind {
        FAVICON,
        ENDPOINT,
        UNKNOWN,
        MALFORMED
    }

    public static RequestTarget parse(@Nullable String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || FAVICON_PATH.equals(rawPath)) {
            return FAVICON_PATH.equals(rawPath) ? new RequestTarget(Kind.FAVICON, null, null) : unknown();
        }

        List<String> segments;
        try {
            segments = split(rawPath);
        } catch (IllegalArgumentException e) {
            // URLDecoder rejects broken escape sequences such as "%zz" or a trailing "%".
            return malformed();
        }
        if (segments.size() < 2 || !API_ROOT.equals(segments.get(0))) {
            return unknown();
        }
        if (segments.size() > 3 || segments.stream().anyMatch(String::isEmpty)) {
            return malformed();
        }

        String endpoint = segments.get(1);
        if (!VALID_ENDPOINT.matcher(endpoint).matches()) {
            return malformed();
        }
        if (segments.size() == 2) {
            return new RequestTarget(Kind.ENDPOINT, endpoint, null);
        }

        String username = segments.get(2);
        return isAcceptableUsername(username)
                ? new RequestTarget(Kind.ENDPOINT, endpoint, username)
                : malformed();
    }

    private static RequestTarget unknown() {
        return new RequestTarget(Kind.UNKNOWN, null, null);
    }

    private static RequestTarget malformed() {
        return new RequestTarget(Kind.MALFORMED, null, null);
    }

    private static boolean isAcceptableUsername(String username) {
        if (username.isBlank() || username.length() > MAX_USERNAME_LENGTH) {
            return false;
        }
        for (int index = 0; index < username.length(); index++) {
            char character = username.charAt(index);
            if (Character.isISOControl(character) || Character.isWhitespace(character)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> split(String rawPath) {
        String path = rawPath;
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        if (path.length() > 1 && path.charAt(path.length() - 1) == '/') {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return List.of();
        }

        List<String> segments = new ArrayList<>();
        for (String rawSegment : path.split("/", -1)) {
            segments.add(rawSegment.isEmpty() ? "" : percentDecode(rawSegment));
        }
        return segments;
    }

    private static String percentDecode(String segment) {
        return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
