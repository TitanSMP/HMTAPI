package de.jumpstone.hmtapi.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import spark.Spark;
import de.jumpstone.hmtapi.HMTAPI;

public class ReloadCommand implements CommandExecutor {
    private HMTAPI plugin;

    public ReloadCommand(HMTAPI hmtApi) {
        plugin = hmtApi;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s,
            @NotNull String[] strings) {
        if (strings[0].equals("reload")) {
            if (!commandSender.hasPermission("apimachine.reload")) {
                commandSender.sendMessage(ChatColor.DARK_RED + "You do not have permission to use this plugin.");
                return true;
            }
            plugin.reloadConfigValues();
            commandSender.sendMessage("Reloaded APIMachine.");
            return true;
        }
        return false;
    }
}
