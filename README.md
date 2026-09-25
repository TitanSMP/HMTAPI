# HMTAPI

RESTful HTTP API for Paper servers, configured through PlaceholderAPI placeholders.

HMTAPI is a fork of [APIMachine](https://modrinth.com/project/9eIn1bYM), developed in cooperation
with HM Gaming for TitanSMP. It replaces the original Spark/Jetty stack with the JDK's built-in HTTP
server, so the plugin ships no third-party libraries at all.

> **User documentation and the full configuration reference live at
> [gaming.henrymeyer.de/projects/plugins/hmtapi/](https://gaming.henrymeyer.de/projects/plugins/hmtapi/).**
> This README is aimed at people who want to build, read or extend the code.

## Requirements

|                    |                                                                           |
| ------------------ | ------------------------------------------------------------------------- |
| Server             | Paper 26.3 or newer (`api-version: 26.3`)                                 |
| Java               | 25                                                                        |
| Runtime dependency | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) |
| Build              | [Maven](https://maven.apache.org/install.html) 3.9+ and a JDK 25          |

## Building

```bash
mvn clean verify
```

This compiles, runs the test suite and writes `target/HMTAPI-<version>.jar`. The jar has no bundled
dependencies, so it is deployed by copying it into the server's `plugins` folder.

`mvn clean install` additionally publishes the artifact to your local repository.

## Architecture

The plugin is split so that all Bukkit access lives on the server thread and the HTTP layer stays
free of it.

| Class                    | Responsibility                                                                       |
| ------------------------ | ------------------------------------------------------------------------------------ |
| `HMTAPI`                 | `JavaPlugin` lifecycle, config loading, and the `ApiContext` implementation          |
| `api/ApiServer`          | HTTP server, routing, status codes, request/response handling                        |
| `api/ApiContext`         | Everything `ApiServer` needs from the plugin; the seam that makes it testable        |
| `api/ApiSettings`        | Immutable, validated view of the global config                                       |
| `api/EndpointRegistry`   | Immutable snapshot of the configured endpoints                                       |
| `api/Endpoint`           | One configured endpoint: name, `require_player`, key/value templates, optional error |
| `api/RequestTarget`      | Raw request path to route classification                                             |
| `api/TemplateRenderer`   | Placeholder substitution, pure functions only                                        |
| `api/Placeholders`       | Defensive bridge to PlaceholderAPI                                                   |
| `api/Json`               | Shared Gson instance for responses                                                   |
| `commands/ReloadCommand` | `/hmtapi reload` and its tab completion                                              |

### Threading model

`com.sun.net.httpserver.HttpServer` dispatches onto a `ThreadPoolExecutor` (2 core, 8 max, 60 s idle
timeout) using threads named `HMTAPI-HTTP-*`. Those threads must never call the Bukkit API, because
Bukkit is not thread safe.

The flow for one request:

1. A worker thread parses the path (`RequestTarget`) and reads the immutable `EndpointRegistry` and
   `ApiSettings` snapshot.
2. `HMTAPI.callSync` schedules a single `Callable` on the server thread and returns a
   `CompletableFuture`.
3. The worker awaits that future for `request_timeout_seconds` (1–60 s, default 5 s).
4. The `Callable` resolves the player, substitutes placeholders and returns the JSON payload.

Consequences worth knowing before you change this code:

- The timeout is a safety valve, not a scheduling hint. A timed-out request still completes its task
  on the server thread; only the HTTP response is abandoned. Keep the callable cheap.
- `context.isActive()` distinguishes "plugin is shutting down" from a genuine failure so shutdown
  does not produce spurious `500`s in the log.
- Player lookups use `Server#getOfflinePlayerIfCached` first and only fall back to
  `Server#getOfflinePlayer` for unknown names, because the fallback hits the database.

### Configuration and reloads

`EndpointRegistry` and `ApiSettings` are replaced wholesale on reload and are `volatile` fields on
the plugin. A request therefore always observes one consistent snapshot, never a half-written
`FileConfiguration`. Everything in them is unmodifiable; endpoint values keep their `config.yml`
order so the JSON key order is stable.

`port` and `bind_address` cannot be changed at runtime. A reload that changes them logs a warning
telling the operator to restart.

### Routing rules

`RequestTarget` classifies a raw path into `ENDPOINT`, `FAVICON`, `UNKNOWN` or `MALFORMED`:

- Endpoint names must match `[A-Za-z0-9_-]{1,64}`.
- Usernames may be at most 32 characters and may not contain whitespace or control characters.
- Percent-encoded segments are decoded; `+` is preserved as a literal plus rather than a space.
- Broken escape sequences such as `%zz` are reported as `MALFORMED` instead of throwing.

### HTTP behaviour that is deliberately unusual

- Errors that describe a _configuration_ problem return `200` with an `{"error": "..."}` body. This
  is the original APIMachine behaviour and is kept for client compatibility.
- Errors that describe a _bad request_ return a real status code: `400` for a malformed path or a
  missing username, `404` for an unknown route, `405` for anything but `GET`/`HEAD`, `503` on
  timeout or shutdown, `500` on unexpected failures.
- A body of a rejected request is drained up to 64 KiB before responding, so keep-alive connections
  stay usable. Larger bodies are abandoned and the connection is closed.
- `HEAD` is answered with the correct status and headers but no body.

## Testing

```bash
mvn test
```

`src/test/java/de/jumpstone/hmtapi/api/` holds unit tests for the pure logic
(`RequestTarget`, `TemplateRenderer`, `Json`, `ApiSettings`, `EndpointRegistry`) and
`ApiServerTest` for the HTTP layer. The latter starts a real `ApiServer` on a free port and drives
it with `java.net.http.HttpClient`; the Bukkit surface is stubbed through `ApiContext`, with
`OfflinePlayer` backed by a `java.lang.reflect.Proxy`. `TestResources` loads YAML fixtures without
requiring a running server.

`ApiServerTest` also has a raw-socket helper, because `HttpClient` refuses to build a URI with a
broken escape sequence and a few edge cases can only be reached by a client that sends the bytes
unchecked.

No test needs a Minecraft server, and none touches the network.

## Contributing

1. Fork the repository and create a branch.
2. Keep the build green: `mvn clean verify` must pass with no compiler warnings. The compiler runs
   with `-Xlint:all,-serial,-processing`.
3. Add tests for new behaviour. Config parsing and HTTP status codes are the areas most likely to
   regress silently.
4. Open a pull request against `main`. CI runs the same `mvn -B --batch-mode clean verify` on JDK 25
   and uploads the jar as a workflow artifact.

### Adding a new placeholder

1. Add a `static` substitution method to `TemplateRenderer`, keeping it free of Bukkit types.
2. Call it from `ApiServer#buildPayload` in the correct order. Built-in placeholders run before
   `{papi:...}` so a value produced by PlaceholderAPI is never re-substituted.
3. Cover it in `TemplateRendererTest`.

### Adding a new config option

1. Add a field and a validated reader to `ApiSettings`; invalid values log a warning and fall back to
   the default rather than throwing.
2. Document it in `src/main/resources/config.yml`.
3. Add a case to `ApiSettingsTest`.

## Links

- Documentation: [gaming.henrymeyer.de/projects/plugins/hmtapi/](https://gaming.henrymeyer.de/projects/plugins/hmtapi/)
- Upstream project: [APIMachine](https://modrinth.com/project/9eIn1bYM)
- Original wiki: [mallusrgreat.gitbook.io](https://mallusrgreat.gitbook.io/mallusrgreats-plugins/)

## License

[GPL-3.0](https://choosealicense.com/licenses/gpl-3.0/)
