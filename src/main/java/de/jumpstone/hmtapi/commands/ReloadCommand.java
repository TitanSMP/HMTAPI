package de.jumpstone.hmtapi.commands;

import de.jumpstone.hmtapi.HMTAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class ReloadCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "hmtapi.reload";

    private static final String RELOAD = "reload";
    private static final List<String> SUBCOMMANDS = List.of(RELOAD);

    private final HMTAPI plugin;

    public ReloadCommand(HMTAPI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.DARK_RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " " + RELOAD, NamedTextColor.YELLOW));
            return true;
        }
        if (!RELOAD.equalsIgnoreCase(args[0])) {
            sender.sendMessage(Component.text("Unknown subcommand '" + args[0] + "'. Usage: /"
                    + label + " " + RELOAD, NamedTextColor.RED));
            return true;
        }
        if (args.length > 1) {
            sender.sendMessage(Component.text("Too many arguments. Usage: /" + label + " " + RELOAD, NamedTextColor.RED));
            return true;
        }

        plugin.reload();
        sender.sendMessage(Component.text("Reloaded HMTAPI.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(subcommand -> subcommand.startsWith(prefix)).toList();
    }
}
