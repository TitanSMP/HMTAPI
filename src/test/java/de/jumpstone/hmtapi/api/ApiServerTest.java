package de.jumpstone.hmtapi.api;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerTest {

    private static final Logger LOGGER = Logger.getLogger(ApiServerTest.class.getName());
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final long LAST_LOGIN = 1_700_000_000_000L;
    private static final String LAST_LOGIN_FORMATTED = FORMATTER.format(Instant.ofEpochMilli(LAST_LOGIN));

    private ApiServer server;
    private HttpClient client;
    private int port;
    private TestContext context;

    @BeforeEach
    void startServer() throws IOException {
        this.port = freePort();
        this.context = new TestContext(port);
        this.server = new ApiServer(context);
        assertTrue(server.start(context.settings()), "the server should start");
        assertTrue(server.isRunning());
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void servesConfiguredEndpoints() throws Exception {
        HttpResponse<String> response = get("/api/global");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        assertEquals("{\"server_name\":\"%server_name%\",\"players\":\"2\"}", response.body());
    }

    @Test
    void rendersBuiltInPlaceholders() throws Exception {
        HttpResponse<String> response = get("/api/users/Notch");

        assertEquals(200, response.statusCode());
        assertEquals("{\"username\":\"Notch\",\"last_login\":\"" + LAST_LOGIN_FORMATTED
                + "\",\"last_seen\":\"never\",\"balance\":\"%vault_eco_balance%\"}", response.body());
    }

    @Test
    void usesTheConfiguredPlaceholderResolver() throws Exception {
        context.placeholderResolver = placeholder -> "resolved-" + placeholder;

        HttpResponse<String> response = get("/api/users/Notch");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"balance\":\"resolved-%vault_eco_balance%\""), response.body());
    }

    @Test
    void reportsUnknownEndpoints() throws Exception {
        HttpResponse<String> response = get("/api/missing");

        assertEquals(200, response.statusCode());
        assertEquals("{\"error\":\"Endpoint 'missing' not found.\"}", response.body());
    }

    @Test
    void reportsAMissingEndpointsSection() throws Exception {
        context.registry = EndpointRegistry.empty();

        HttpResponse<String> response = get("/api/global");

        assertEquals(200, response.statusCode());
        assertEquals("{\"error\":\"endpoints not found.\"}", response.body());
    }

    @Test
    void requiresAUsernameWhenTheEndpointNeedsAPlayer() throws Exception {
        HttpResponse<String> response = get("/api/users");

        assertEquals(400, response.statusCode());
        assertEquals("{\"error\":\"Endpoint 'users' requires a player name: /api/users/{username}\"}", response.body());
    }

    @Test
    void acceptsAUsernameForEndpointsThatDoNotNeedAPlayer() throws Exception {
        assertEquals(200, get("/api/global/Notch").statusCode());
    }

    @Test
    void reportsMisconfiguredEndpoints() throws Exception {
        context.registry = EndpointRegistry.load(TestResources.configuration("""
                endpoints:
                  broken:
                    require_player: true
                """), LOGGER);

        HttpResponse<String> response = get("/api/broken/Notch");

        assertEquals(200, response.statusCode());
        assertEquals("{\"error\":\"endpoints.broken.object not found.\"}", response.body());
    }

    @Test
    void answersFaviconRequests() throws Exception {
        HttpResponse<String> response = get("/favicon.ico");

        assertEquals(200, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void answersUnknownPaths() throws Exception {
        HttpResponse<String> response = get("/nope");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Not found."), response.body());
    }

    @Test
    void answersMalformedPaths() throws Exception {
        HttpResponse<String> response = get("/api/users/Notch/extra");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Malformed request path"), response.body());
    }

    @Test
    void rejectsUnsupportedMethods() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/global")).POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
        assertEquals("GET, HEAD", response.headers().firstValue("Allow").orElseThrow());
        assertEquals("{\"error\":\"Method POST is not supported, use GET.\"}", response.body());
    }

    @Test
    void rejectsUnsupportedMethodsWithABody() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/global"))
                .PUT(HttpRequest.BodyPublishers.ofString("{\"a\":1}"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
        assertEquals("{\"error\":\"Method PUT is not supported, use GET.\"}", response.body());
    }

    @Test
    void answersHeadRequestsWithoutABody() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/global")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void answersBrokenPercentEncodingAsMalformed() throws Exception {
        String response = rawRequest("GET /api/users/%zz HTTP/1.1");

        assertTrue(response.startsWith("HTTP/1.1 400"), response);
    }

    @Test
    void rejectsUnsupportedMethodsWithAnOversizedBody() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/global"))
                .POST(HttpRequest.BodyPublishers.ofString("x".repeat(256 * 1024)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
        assertEquals("{\"error\":\"Method POST is not supported, use GET.\"}", response.body());
    }

    @Test
    void keepsServingAfterAnOversizedBody() throws Exception {
        HttpRequest oversized = HttpRequest.newBuilder(uri("/api/global"))
                .POST(HttpRequest.BodyPublishers.ofString("x".repeat(256 * 1024)))
                .build();
        client.send(oversized, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, get("/api/global").statusCode());
    }

    @Test
    void decodesPercentEncodedSegments() throws Exception {
        HttpResponse<String> response = get("/api/users/Notch%5F42");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"username\":\"Notch_42\""), response.body());
    }

    @Test
    void escapesJsonSpecialCharacters() throws Exception {
        context.registry = EndpointRegistry.load(TestResources.configuration("""
                endpoints:
                  tricky:
                    object:
                      value: 'a\\b"c<d>&e'
                """), LOGGER);

        HttpResponse<String> response = get("/api/tricky");

        assertEquals(200, response.statusCode());
        assertEquals("{\"value\":\"a\\\\b\\\"c<d>&e\"}", response.body());
    }

    @Test
    void reportsRenderingFailures() throws Exception {
        context.failure = new IllegalStateException("boom");

        HttpResponse<String> response = get("/api/global");

        assertEquals(500, response.statusCode());
        assertEquals("{\"error\":\"Failed to collect the requested data.\"}", response.body());
    }

    @Test
    void reportsRejectedRequestsWhileShuttingDown() throws Exception {
        context.active = false;

        HttpResponse<String> response = get("/api/global");

        assertEquals(503, response.statusCode());
        assertEquals("{\"error\":\"The plugin is shutting down.\"}", response.body());
    }

    @Test
    void stopIsIdempotentAndReleasesThePort() {
        server.stop();
        server.stop();

        assertFalse(server.isRunning());
        assertTrue(bindable(port), "the port should be free again");
    }

    @Test
    void startDoesNotCreateASecondServer() throws Exception {
        assertTrue(server.start(context.settings()));
        assertEquals(200, get("/api/global").statusCode());
    }

    @Test
    void reportsAnUnusableAddress() {
        ApiServer second = new ApiServer(context);

        assertFalse(second.start(context.settings()), "the port is already taken");
        assertFalse(second.isRunning());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    /**
     * Sends a request over a raw socket. {@link HttpClient} refuses to build a URI with a broken
     * escape sequence, so a client that passes the bytes through unchecked can only be simulated
     * this way.
     */
    private String rawRequest(String requestLine) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(5));
            OutputStream out = socket.getOutputStream();
            out.write((requestLine + "\r\nHost: 127.0.0.1:" + port + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static boolean bindable(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static OfflinePlayer offlinePlayer(String name, long lastLogin, long lastSeen) {
        return (OfflinePlayer) Proxy.newProxyInstance(
                ApiServerTest.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getLastLogin" -> lastLogin;
                    case "getLastSeen" -> lastSeen;
                    case "isOnline", "isConnected" -> false;
                    case "toString" -> "OfflinePlayer[" + name + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class TestContext implements ApiContext {

        private final ApiSettings settings;
        private volatile EndpointRegistry registry = EndpointRegistry.load(TestResources.configuration("""
                endpoints:
                  users:
                    require_player: true
                    object:
                      username: "{username}"
                      last_login: "{last_login}"
                      last_seen: "{last_seen}"
                      balance: "{papi:%vault_eco_balance%}"
                  global:
                    require_player: false
                    object:
                      server_name: "{papi:%server_name%}"
                      players: 2
                """), LOGGER);
        private volatile boolean active = true;
        private volatile Function<String, String> placeholderResolver = Function.identity();
        private volatile Throwable failure;

        private TestContext(int port) {
            this.settings = new ApiSettings(port, "127.0.0.1", ZoneOffset.UTC, FORMATTER, "never", 5);
        }

        @Override
        public Logger logger() {
            return LOGGER;
        }

        @Override
        public EndpointRegistry registry() {
            return registry;
        }

        @Override
        public ApiSettings settings() {
            return settings;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public OfflinePlayer player(String username) {
            return offlinePlayer(username, LAST_LOGIN, 0L);
        }

        @Override
        public String resolvePlaceholders(OfflinePlayer player, String placeholder) {
            return placeholderResolver.apply(placeholder);
        }

        @Override
        public <T> CompletableFuture<T> callSync(Callable<T> task) {
            CompletableFuture<T> future = new CompletableFuture<>();
            if (!active) {
                future.completeExceptionally(new IllegalStateException("HMTAPI is not accepting requests."));
                return future;
            }
            if (failure != null) {
                future.completeExceptionally(failure);
                return future;
            }
            try {
                future.complete(task.call());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
            return future;
        }
    }
}
