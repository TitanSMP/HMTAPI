package de.jumpstone.hmtapi;

import de.jumpstone.hmtapi.api.ApiContext;
import de.jumpstone.hmtapi.api.ApiServer;
import de.jumpstone.hmtapi.api.ApiSettings;
import de.jumpstone.hmtapi.api.EndpointRegistry;
import de.jumpstone.hmtapi.api.Placeholders;
import de.jumpstone.hmtapi.commands.ReloadCommand;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class HMTAPI extends JavaPlugin implements ApiContext {

    private volatile EndpointRegistry registry = EndpointRegistry.empty();
    private volatile ApiSettings settings = ApiSettings.defaults();
    private ApiServer apiServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readConfiguration();

        PluginCommand pluginCommand = getCommand("hmtapi");
        if (pluginCommand == null) {
            getLogger().severe("The '/hmtapi' command is missing from plugin.yml, disabling HMTAPI.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ReloadCommand reloadCommand = new ReloadCommand(this);
        pluginCommand.setExecutor(reloadCommand);
        pluginCommand.setTabCompleter(reloadCommand);

        this.apiServer = new ApiServer(this);
        if (!this.apiServer.start(settings)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ApiSettings active = settings;
        getLogger().info(() -> "HMTAPI is serving the API on " + active.address() + " ("
                + registry.names().size() + " endpoints, PlaceholderAPI "
                + (Placeholders.isAvailable(this) ? "available" : "unavailable") + ").");
    }

    @Override
    public void onDisable() {
        if (this.apiServer != null) {
            this.apiServer.stop();
            this.apiServer = null;
        }
    }

    public void reload() {
        reloadConfig();
        readConfiguration();
    }

    private void readConfiguration() {
        ApiSettings previous = this.settings;
        ApiSettings updated = ApiSettings.load(getConfig(), getLogger());
        this.settings = updated;
        this.registry = EndpointRegistry.load(getConfig(), getLogger());

        if (previous.port() != updated.port() || !previous.bindAddress().equals(updated.bindAddress())) {
            getLogger().warning("The API address changed from " + previous.address() + " to " + updated.address()
                    + ". Restart the server to apply it.");
        }
    }

    @Override
    public Logger logger() {
        return getLogger();
    }

    @Override
    public boolean isActive() {
        return isEnabled() && !getServer().isStopping();
    }

    @Override
    public OfflinePlayer player(String username) {
        Server server = getServer();
        OfflinePlayer cached = server.getOfflinePlayerIfCached(username);
        return cached != null ? cached : server.getOfflinePlayer(username);
    }

    @Override
    public String resolvePlaceholders(OfflinePlayer player, String placeholder) {
        return Placeholders.resolve(this, player, placeholder);
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
    public <T> CompletableFuture<T> callSync(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (!isEnabled() || getServer().isStopping()) {
            future.completeExceptionally(new IllegalStateException("HMTAPI is not accepting requests."));
            return future;
        }
        try {
            getServer().getScheduler().runTask(this, () -> {
                try {
                    future.complete(task.call());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}
