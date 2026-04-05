package de.nicouschulas.betterbouncyblocks;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final BetterBouncyBlocks plugin;

    public CommandHandler(BetterBouncyBlocks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("betterbouncyblocks.reload")) {
                plugin.reloadConfig();
                sender.sendMessage(plugin.getFormattedMessage("reload-success"));
            } else {
                sender.sendMessage(plugin.getFormattedMessage("no-permission"));
            }
            return true;
        }

        sender.sendMessage(plugin.getFormattedMessage("command-usage"));
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("betterbouncyblocks.reload")) {
                completions.add("reload");
            }
        }
        return completions;
    }
}