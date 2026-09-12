package de.nicouschulas.betterbouncyblocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.milkbowl.vault.economy.EconomyResponse;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final BetterBouncyBlocks plugin;

    public CommandHandler(BetterBouncyBlocks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getFormattedMessage("command-usage"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "buy" -> handleBuy(sender, args);
            default -> sender.sendMessage(plugin.getFormattedMessage("command-usage"));
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("betterbouncyblocks.reload")) {
            sender.sendMessage(plugin.getFormattedMessage("no-permission"));
            return;
        }

        plugin.reloadConfig();
        sender.sendMessage(plugin.getFormattedMessage("reload-success"));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("betterbouncyblocks.give")) {
            sender.sendMessage(plugin.getFormattedMessage("no-permission"));
            return;
        }

        if (!plugin.getConfig().getBoolean("consumable-block.enabled", true)) {
            sender.sendMessage(plugin.getFormattedMessage("consumable-disabled"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.parse("&cUsage: &7/bbb give <player> [amount]"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getFormattedMessage("player-not-found"));
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getFormattedMessage("invalid-amount"));
                return;
            }
        }

        Material material = getConsumableMaterial();
        if (material == null) {
            sender.sendMessage(plugin.parse("&cInvalid consumable block material in config.yml!"));
            return;
        }

        ItemStack items = new ItemStack(material, amount);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(items);

        String rawGiveMsg = plugin.getConfig().getString("messages.give-success", "")
                .replace("%amount%", String.valueOf(amount))
                .replace("%block%", material.name())
                .replace("%player%", target.getName());
        sender.sendMessage(plugin.parse(plugin.getConfig().getString("prefix", "") + rawGiveMsg));

        String rawReceiveMsg = plugin.getConfig().getString("messages.receive-success", "")
                .replace("%amount%", String.valueOf(amount))
                .replace("%block%", material.name());
        target.sendMessage(plugin.parse(plugin.getConfig().getString("prefix", "") + rawReceiveMsg));

        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), item);
            }
            target.sendMessage(plugin.getFormattedMessage("inventory-full"));
        }
    }

    private void handleBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.parse("&cThis command can only be executed by players!"));
            return;
        }

        if (!player.hasPermission("betterbouncyblocks.buy")) {
            player.sendMessage(plugin.getFormattedMessage("no-permission"));
            return;
        }

        if (!plugin.getConfig().getBoolean("consumable-block.enabled", false)) {
            player.sendMessage(plugin.getFormattedMessage("consumable-disabled"));
            return;
        }

        if (!plugin.isEconomyAvailable()) {
            player.sendMessage(plugin.getFormattedMessage("vault-disabled"));
            return;
        }

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getFormattedMessage("invalid-amount"));
                return;
            }
        }

        double pricePerBlock = plugin.getConfig().getDouble("consumable-block.price", 50.0);
        double totalPrice = pricePerBlock * amount;

        if (!plugin.getEconomy().has(player, totalPrice)) {
            String rawMsg = plugin.getConfig().getString("messages.not-enough-money", "")
                    .replace("%price%", String.format("%.2f", totalPrice));
            player.sendMessage(plugin.parse(plugin.getConfig().getString("prefix", "") + rawMsg));
            return;
        }

        Material material = getConsumableMaterial();
        if (material == null) {
            player.sendMessage(plugin.parse("&cInvalid consumable block material in config.yml!"));
            return;
        }

        if (isInventoryFull(player, material)) {
            player.sendMessage(plugin.getFormattedMessage("inventory-full"));
            return;
        }

        EconomyResponse response = plugin.getEconomy().withdrawPlayer(player, totalPrice);
        if (!response.transactionSuccess()) {
            player.sendMessage(plugin.parse("&cThe transaction failed, you have not been charged!"));
            return;
        }

        ItemStack items = new ItemStack(material, amount);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(items);

        String rawBuyMsg = plugin.getConfig().getString("messages.buy-success", "")
                .replace("%amount%", String.valueOf(amount))
                .replace("%block%", material.name())
                .replace("%price%", String.format("%.2f", totalPrice));
        player.sendMessage(plugin.parse(plugin.getConfig().getString("prefix", "") + rawBuyMsg));

        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            player.sendMessage(plugin.getFormattedMessage("inventory-full"));
        }
    }

    private boolean isInventoryFull(Player player, Material material) {
        if (player.getInventory().firstEmpty() != -1) {
            return false;
        }
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material && item.getAmount() < item.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    private Material getConsumableMaterial() {
        String blockName = plugin.getConfig().getString("consumable-block.block", "MAGENTA_GLAZED_TERRACOTTA").toUpperCase();
        return Material.matchMaterial(blockName);
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("betterbouncyblocks.reload")) completions.add("reload");
            if (sender.hasPermission("betterbouncyblocks.give")) completions.add("give");
            if (sender.hasPermission("betterbouncyblocks.buy")) completions.add("buy");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            if (sender.hasPermission("betterbouncyblocks.give")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            completions.add("1");
            completions.add("5");
            completions.add("10");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            completions.add("1");
            completions.add("5");
            completions.add("10");
        }

        return completions;
    }
}