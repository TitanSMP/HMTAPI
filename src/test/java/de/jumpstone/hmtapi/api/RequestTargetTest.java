package de.jumpstone.hmtapi.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestTargetTest {

    @Test
    void parsesEndpointWithoutUsername() {
        RequestTarget target = RequestTarget.parse("/api/users");

        assertEquals(RequestTarget.Kind.ENDPOINT, target.kind());
        assertEquals("users", target.endpoint());
        assertNull(target.username());
    }

    @Test
    void parsesEndpointWithUsername() {
        RequestTarget target = RequestTarget.parse("/api/users/Notch");

        assertEquals(RequestTarget.Kind.ENDPOINT, target.kind());
        assertEquals("users", target.endpoint());
        assertEquals("Notch", target.username());
    }

    @Test
    void toleratesTrailingSlash() {
        RequestTarget target = RequestTarget.parse("/api/users/");

        assertEquals(RequestTarget.Kind.ENDPOINT, target.kind());
        assertEquals("users", target.endpoint());
        assertNull(target.username());
    }

    @Test
    void recognizesFavicon() {
        assertEquals(RequestTarget.Kind.FAVICON, RequestTarget.parse("/favicon.ico").kind());
    }

    @Test
    void marksUnknownPathsAsUnknown() {
        assertEquals(RequestTarget.Kind.UNKNOWN, RequestTarget.parse("/").kind());
        assertEquals(RequestTarget.Kind.UNKNOWN, RequestTarget.parse("/api").kind());
        assertEquals(RequestTarget.Kind.UNKNOWN, RequestTarget.parse("/nope/users").kind());
        assertEquals(RequestTarget.Kind.UNKNOWN, RequestTarget.parse("/apiary").kind());
    }

    @Test
    void marksInvalidPathsAsMalformed() {
        assertEquals(RequestTarget.Kind.MALFORMED, RequestTarget.parse("/api/users/Notch/extra").kind());
        assertEquals(RequestTarget.Kind.MALFORMED, RequestTarget.parse("/api//Notch").kind());
        assertEquals(RequestTarget.Kind.MALFORMED, RequestTarget.parse("/api/..").kind());
        assertEquals(RequestTarget.Kind.MALFORMED, RequestTarget.parse("/api/users.object").kind());
        assertEquals(RequestTarget.Kind.MALFORMED, RequestTarget.parse("/api/users/%20").kind());
        assertEquals(RequestTarget.Kind.MALFORMED, RequestTarget.parse("/api/users/" + "a".repeat(33)).kind());
    }

    @Test
    void marksBrokenPercentEncodingAsMalformed() {
        assertEquals(RequestTarget.Kind.MALFORMED, RequestTarget.parse("/api/users/%zz").kind());
        assertEquals(RequestTarget.Kind.MALFORMED, RequestTarget.parse("/api/users/%2").kind());
        assertEquals(RequestTarget.Kind.MALFORMED, RequestTarget.parse("/api/%zz/Notch").kind());
    }

    @Test
    void decodesPercentEncodedSegments() {
        RequestTarget target = RequestTarget.parse("/api/ser%2Dver/Notch%5F42");

        assertEquals("ser-ver", target.endpoint());
        assertEquals("Notch_42", target.username());
    }

    @Test
    void keepsPlusSignInUsername() {
        assertEquals("no+body", RequestTarget.parse("/api/users/no+body").username());
    }

    @Test
    void rejectsNullPath() {
        assertEquals(RequestTarget.Kind.UNKNOWN, RequestTarget.parse(null).kind());
    }
}
