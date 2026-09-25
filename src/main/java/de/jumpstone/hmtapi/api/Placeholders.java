package de.jumpstone.hmtapi.api;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Thin, defensive bridge to PlaceholderAPI. PlaceholderAPI looks up its plugin instance statically,
 * so calling it while it is unavailable or broken would otherwise abort the whole request.
 */
public final class Placeholders {

    private static final String PLACEHOLDER_API = "PlaceholderAPI";

    private Placeholders() {
    }

    public static boolean isAvailable(Plugin plugin) {
        Plugin placeholderApi = plugin.getServer().getPluginManager().getPlugin(PLACEHOLDER_API);
        return placeholderApi != null && placeholderApi.isEnabled();
    }

    public static String resolve(Plugin plugin, OfflinePlayer player, String placeholder) {
        if (!isAvailable(plugin)) {
            return placeholder;
        }
        try {
            String resolved = PlaceholderAPI.setPlaceholders(player, placeholder);
            return resolved == null ? placeholder : resolved;
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    "PlaceholderAPI could not resolve '%" + placeholder + "%', the placeholder was left unresolved.", throwable);
            return placeholder;
        }
    }
}
