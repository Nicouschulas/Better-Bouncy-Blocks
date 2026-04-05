package de.nicouschulas.betterbouncyblocks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Level;

public final class BetterBouncyBlocks extends JavaPlugin implements Listener {

    private Component chatPrefix;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private String latestVersion = null;

    @Override
    public void onEnable() {
        getLogger().info("BetterBouncyBlocks is starting...");

        saveDefaultConfig();
        reloadConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new BounceListener(this), this);

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
        getLogger().info("BetterBouncyBlocks shutdown successfully!");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        loadPrefix();
    }

    private void loadPrefix() {
        String rawPrefix = getConfig().getString("prefix", "&7[&cBBB&7] ");
        this.chatPrefix = legacySerializer.deserialize(rawPrefix);
    }

    public Component getFormattedMessage(String messageKey) {
        String message = getConfig().getString("messages." + messageKey, "Message not found: " + messageKey);
        return chatPrefix.append(legacySerializer.deserialize(message));
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

                    if (json.contains("\"version_number\":\"")) {
                        String fetchedLatestVersion = json.split("\"version_number\":\"")[1].split("\"")[0];

                        if (!currentVersion.equals(fetchedLatestVersion)) {
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
                }
            } catch (IOException | URISyntaxException e) {
                getLogger().log(Level.FINER, "Update checker failed to connect to the server!", e);
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
            if ((notifyMethod.equals("player") || notifyMethod.equals("both")) && player.hasPermission("betterbouncyblocks.update")) {

                Component textComponent = legacySerializer.deserialize("&aA new version of Better Bouncy Blocks is available: " + this.latestVersion + " ");

                Component linkComponent = Component.text("Click to download it at Modrinth", NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.openUrl("https://modrinth.com/project/betterbouncyblocks/versions"));

                Component updateMessage = chatPrefix.append(textComponent).append(linkComponent);
                player.sendMessage(updateMessage);
            }
        }
    }
}
