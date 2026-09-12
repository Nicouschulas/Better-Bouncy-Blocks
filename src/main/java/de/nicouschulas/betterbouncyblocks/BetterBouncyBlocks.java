package de.nicouschulas.betterbouncyblocks;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.Scanner;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetterBouncyBlocks extends JavaPlugin implements Listener {

    private Component chatPrefix;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private BounceListener bounceListener;
    private Economy economy = null;

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    // volatile: written by the async update check, read by the main thread on player join
    private volatile String latestVersion = null;

    @Override
    public void onEnable() {
        getLogger().info("BetterBouncyBlocks is starting...");

        saveDefaultConfig();

        setupEconomy();

        this.bounceListener = new BounceListener(this);
        reloadConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(bounceListener, this);

        CommandHandler commandHandler = new CommandHandler(this);
        Objects.requireNonNull(getCommand("bbb")).setExecutor(commandHandler);
        Objects.requireNonNull(getCommand("bbb")).setTabCompleter(commandHandler);

        int serviceId = 30591;
        new Metrics(this, serviceId);

        checkForUpdates();

        getLogger().info("BetterBouncyBlocks started successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BetterBouncyBlocks is shutting down...");
        getLogger().info("BetterBouncyBlocks shutdown successfully!");
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault not found. Economy features (/bbb buy) will be disabled.");
            return;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().info("No economy provider found. Economy features (/bbb buy) will be disabled.");
            return;
        }

        this.economy = rsp.getProvider();
        getLogger().info("Vault economy hooked successfully!");
    }

    public Economy getEconomy() {
        return this.economy;
    }

    public boolean isEconomyAvailable() {
        return this.economy != null;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        loadPrefix();
        if (bounceListener != null) {
            bounceListener.reloadValues();
        }
    }

    private void loadPrefix() {
        String rawPrefix = getConfig().getString("prefix", "&7[&cBBB&7] ");
        this.chatPrefix = parse(rawPrefix);
    }

    public Component getFormattedMessage(String messageKey) {
        String message = getConfig().getString("messages." + messageKey, "Message not found: " + messageKey);
        return this.chatPrefix.append(parse(message));
    }

    public Component parse(String input) {
        if (input == null) return Component.empty();

        String prepared = input
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&r", "<reset>")
                .replace("&l", "<bold>")
                .replace("&o", "<italic>")
                .replace("&n", "<underlined>")
                .replace("&m", "<strikethrough>")
                .replace("&k", "<obfuscated>");

        prepared = HEX_PATTERN.matcher(prepared).replaceAll("<#$1>");

        return miniMessage.deserialize(prepared);
    }

    private void checkForUpdates() {
        if (!getConfig().getBoolean("update-checker.enabled", true)) {
            return;
        }

        final String notifyMethod = getConfig().getString("update-checker.notify-method", "both");
        final String currentVersion = getPluginMeta().getVersion();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URI("https://api.modrinth.com/v2/project/wUsXrkAK/version").toURL();
                try (InputStream inputStream = url.openStream(); Scanner scanner = new Scanner(inputStream)) {
                    String json = scanner.useDelimiter("\\A").next();

                    // Modrinth returns versions sorted newest first; pick the newest release,
                    // so snapshots or betas are never suggested as an update.
                    String fetchedLatestVersion = null;
                    JsonArray versions = JsonParser.parseString(json).getAsJsonArray();
                    for (JsonElement element : versions) {
                        JsonObject version = element.getAsJsonObject();
                        if ("release".equals(version.get("version_type").getAsString())) {
                            fetchedLatestVersion = version.get("version_number").getAsString();
                            break;
                        }
                    }

                    if (fetchedLatestVersion != null && !currentVersion.equals(fetchedLatestVersion)) {
                        this.latestVersion = fetchedLatestVersion;

                        if (notifyMethod.equalsIgnoreCase("console") || notifyMethod.equalsIgnoreCase("both")) {
                            getLogger().warning("-----------------------------------------------------");
                            getLogger().warning("A new version of BetterBouncyBlocks is available!");
                            getLogger().warning("Current version: " + currentVersion);
                            getLogger().warning("Latest version: " + this.latestVersion);
                            getLogger().warning("Download it here: https://modrinth.com/project/betterbouncyblocks/versions");
                            getLogger().warning("-----------------------------------------------------");
                        }
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Update checker failed to process the response: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("update-checker.enabled", true)) {
            return;
        }

        String notifyMethod = getConfig().getString("update-checker.notify-method", "both");

        if (this.latestVersion != null) {
            Player player = event.getPlayer();
            if ((notifyMethod.equalsIgnoreCase("player") || notifyMethod.equalsIgnoreCase("both")) && player.hasPermission("betterbouncyblocks.update")) {

                Component textComponent = parse("&aA new version of Better Bouncy Blocks is available: " + this.latestVersion + " ");

                Component linkComponent = Component.text("Click to download it at Modrinth", NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.openUrl("https://modrinth.com/project/betterbouncyblocks/versions"));

                Component updateMessage = this.chatPrefix.append(textComponent).append(linkComponent);
                player.sendMessage(updateMessage);
            }
        }
    }
}