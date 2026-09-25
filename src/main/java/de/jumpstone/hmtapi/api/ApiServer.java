package de.jumpstone.hmtapi.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.OfflinePlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * The HTTP layer, built on the JDK's built-in HTTP server so the plugin does not have to ship an
 * embedded servlet container.
 * <p>
 * Worker threads never touch the Bukkit API directly: the payload is assembled in a single task on
 * the server thread and awaited with a timeout, which keeps the Minecraft server responsive and the
 * response data consistent.
 */
public final class ApiServer {

    private static final String FAVICON_PATH = "/favicon.ico";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String ROUTE_HINT = "Available routes: /api/{endpoint}, /api/{endpoint}/{username}, " + FAVICON_PATH;
    private static final int BACKLOG = 64;
    private static final int CORE_THREADS = 2;
    private static final int MAX_THREADS = 8;
    private static final int MAX_DISCARDED_REQUEST_BYTES = 64 * 1024;
    private static final long THREAD_IDLE_TIMEOUT_SECONDS = 60L;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 2L;

    private final ApiContext context;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile HttpServer server;
    private volatile ExecutorService workers;

    public ApiServer(ApiContext context) {
        this.context = context;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean start(ApiSettings settings) {
        if (!running.compareAndSet(false, true)) {
            context.logger().warning("The HTTP server is already running, not starting a second one.");
            return true;
        }
        try {
            InetSocketAddress address = new InetSocketAddress(settings.bindAddress(), settings.port());
            HttpServer created = HttpServer.create(address, BACKLOG);
            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    CORE_THREADS, MAX_THREADS, THREAD_IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(), new WorkerThreadFactory());
            pool.allowCoreThreadTimeOut(true);
            created.setExecutor(pool);
            created.createContext("/", this::handle);
            created.start();
            this.server = created;
            this.workers = pool;
            return true;
        } catch (IOException | RuntimeException e) {
            running.set(false);
            context.logger().log(Level.SEVERE, "Could not start the HTTP API on " + settings.address()
                    + ". Please check the 'port' and 'bind_address' settings in config.yml.", e);
            return false;
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        HttpServer currentServer = server;
        ExecutorService currentWorkers = workers;
        server = null;
        workers = null;

        if (currentServer != null) {
            currentServer.stop(0);
        }
        if (currentWorkers != null) {
            currentWorkers.shutdownNow();
            try {
                if (!currentWorkers.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    context.logger().warning("HTTP worker threads did not terminate within "
                            + SHUTDOWN_TIMEOUT_SECONDS + " seconds.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handle(HttpExchange exchange) {
        try (exchange) {
            respond(exchange);
        } catch (IOException e) {
            context.logger().log(Level.FINE, "Failed to write an HTTP response.", e);
        } catch (RuntimeException e) {
            context.logger().log(Level.WARNING, "Unexpected error while handling " + exchange.getRequestURI(), e);
        }
    }

    private void respond(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        boolean isHeadRequest = "HEAD".equals(method);
        if (!"GET".equals(method) && !isHeadRequest) {
            discardRequestBody(exchange);
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            send(exchange, 405, Json.error("Method " + method + " is not supported, use GET."), true);
            return;
        }
        boolean includeBody = !isHeadRequest;

        String rawPath = exchange.getRequestURI().getRawPath();
        RequestTarget target = RequestTarget.parse(rawPath);
        switch (target.kind()) {
            case FAVICON -> send(exchange, 200, null, includeBody);
            case UNKNOWN -> send(exchange, 404, Json.error("Not found. " + ROUTE_HINT), includeBody);
            case MALFORMED -> send(exchange, 400, Json.error("Malformed request path '" + rawPath + "'. " + ROUTE_HINT), includeBody);
            case ENDPOINT -> handleEndpoint(exchange, target, includeBody);
        }
    }

    private void handleEndpoint(HttpExchange exchange, RequestTarget target, boolean includeBody) throws IOException {
        ApiSettings settings = context.settings();
        EndpointRegistry registry = context.registry();

        if (!registry.isConfigured()) {
            send(exchange, 200, Json.error("endpoints not found."), includeBody);
            return;
        }
        Endpoint endpoint = registry.get(target.endpoint());
        if (endpoint == null) {
            send(exchange, 200, Json.error("Endpoint '" + target.endpoint() + "' not found."), includeBody);
            return;
        }
        if (endpoint.error() != null) {
            send(exchange, 200, Json.error(endpoint.error()), includeBody);
            return;
        }

        String username = target.username();
        if (username == null && endpoint.requirePlayer()) {
            send(exchange, 400, Json.error("Endpoint '" + endpoint.name() + "' requires a player name: /api/"
                    + endpoint.name() + "/{username}"), includeBody);
            return;
        }

        try {
            send(exchange, 200, render(endpoint, username, settings), includeBody);
        } catch (TimeoutException e) {
            context.logger().warning("Timed out while collecting data for endpoint '" + endpoint.name()
                    + "'. Is the server overloaded?");
            send(exchange, 503, Json.error("Timed out while collecting the requested data."), includeBody);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            send(exchange, 503, Json.error("The request was interrupted because the plugin is shutting down."), includeBody);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (!context.isActive()) {
                send(exchange, 503, Json.error("The plugin is shutting down."), includeBody);
                return;
            }
            context.logger().log(Level.WARNING, "Failed to collect data for endpoint '" + endpoint.name() + "'.", cause);
            send(exchange, 500, Json.error("Failed to collect the requested data."), includeBody);
        }
    }

    private String render(Endpoint endpoint, String username, ApiSettings settings)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<String> payload = context.callSync(() -> buildPayload(endpoint, username, settings));
        return payload.get(settings.requestTimeoutSeconds(), TimeUnit.SECONDS);
    }

    private String buildPayload(Endpoint endpoint, String username, ApiSettings settings) {
        OfflinePlayer player = username == null ? null : context.player(username);
        String resolvedName = player == null ? "" : Optional.ofNullable(player.getName()).orElse(username);
        String lastLogin = player == null ? settings.neverSeenValue() : format(player.getLastLogin(), settings);
        String lastSeen = player == null ? settings.neverSeenValue() : format(player.getLastSeen(), settings);

        Map<String, String> payload = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : endpoint.values().entrySet()) {
            String value = entry.getValue();
            value = TemplateRenderer.applyUsername(value, resolvedName);
            value = TemplateRenderer.applyLastLogin(value, lastLogin);
            value = TemplateRenderer.applyLastSeen(value, lastSeen);
            value = TemplateRenderer.applyPlaceholders(value, key -> context.resolvePlaceholders(player, key));
            payload.put(entry.getKey(), value);
        }
        return Json.write(payload);
    }

    private static String format(long epochMilli, ApiSettings settings) {
        if (epochMilli <= 0L) {
            return settings.neverSeenValue();
        }
        return settings.dateFormat().format(Instant.ofEpochMilli(epochMilli).atZone(settings.zone()));
    }

    private static void send(HttpExchange exchange, int status, String body, boolean includeBody) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", JSON_CONTENT_TYPE);
        exchange.sendResponseHeaders(status, includeBody ? payload.length : -1);
        if (includeBody) {
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(payload);
            }
        }
    }

    private static void discardRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream requestBody = exchange.getRequestBody()) {
            byte[] buffer = new byte[1024];
            int discarded = 0;
            int read;
            while (discarded < MAX_DISCARDED_REQUEST_BYTES && (read = requestBody.read(buffer)) > 0) {
                discarded += read;
            }
        }
    }

    private static final class WorkerThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "HMTAPI-HTTP-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
