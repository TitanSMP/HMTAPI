package de.jumpstone.hmtapi.api;

import org.bukkit.OfflinePlayer;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Everything the HTTP layer needs from the plugin. The settings and the endpoint registry are
 * exposed as immutable snapshots so a reload is picked up atomically by the next request, and all
 * Bukkit access is funnelled through this interface to keep it on the server thread.
 */
public interface ApiContext {

    Logger logger();

    EndpointRegistry registry();

    ApiSettings settings();

    boolean isActive();

    OfflinePlayer player(String username);

    String resolvePlaceholders(OfflinePlayer player, String placeholder);

    <T> CompletableFuture<T> callSync(Callable<T> task);
}
