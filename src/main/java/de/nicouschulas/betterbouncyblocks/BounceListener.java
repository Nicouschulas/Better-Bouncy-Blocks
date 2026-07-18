package de.nicouschulas.betterbouncyblocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

public class BounceListener implements Listener {

    private final BetterBouncyBlocks plugin;
    private final Map<UUID, Long> fallDamageImmunity = new HashMap<>();
    private final boolean isWorldGuardPresent;

    private Material targetMaterial;
    private double velocityMultiplier;
    private int noDamageTicks;
    private boolean wgEnabled;
    private final Set<String> allowedRegions = new HashSet<>();

    public BounceListener(BetterBouncyBlocks plugin) {
        this.plugin = plugin;
        this.isWorldGuardPresent = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    public void reloadValues() {
        String configBlockName = plugin.getConfig().getString("block", "SLIME_BLOCK").toUpperCase();
        this.targetMaterial = Material.matchMaterial(configBlockName);
        this.velocityMultiplier = plugin.getConfig().getDouble("velocity-multiplier", 2.0);
        this.noDamageTicks = plugin.getConfig().getInt("no-damage-ticks", 500);
        this.wgEnabled = plugin.getConfig().getBoolean("worldguard.enabled", false);

        this.allowedRegions.clear();
        for (String region : plugin.getConfig().getStringList("worldguard.regions")) {
            this.allowedRegions.add(region.toLowerCase());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        if (targetMaterial == null) return;

        Player player = event.getPlayer();

        if (!player.hasPermission("betterbouncyblocks.use")) {
            return;
        }

        Block blockBelow = event.getTo().getBlock().getRelative(BlockFace.DOWN);

        if (blockBelow.getType() == targetMaterial) {

            if (!isInAllowedRegion(blockBelow.getLocation())) {
                return;
            }

            player.setVelocity(new Vector(player.getVelocity().getX(), velocityMultiplier, player.getVelocity().getZ()));

            long immunityTimeEnd = System.currentTimeMillis() + (noDamageTicks * 50L);
            fallDamageImmunity.put(player.getUniqueId(), immunityTimeEnd);
        }
    }

    private boolean isInAllowedRegion(Location loc) {
        if (!isWorldGuardPresent || !wgEnabled) {
            return true;
        }

        if (allowedRegions.isEmpty()) {
            return false;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        for (ProtectedRegion region : set) {
            if (allowedRegions.contains(region.getId().toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Long immunityTimeEnd = fallDamageImmunity.get(player.getUniqueId());
            if (immunityTimeEnd != null) {
                if (System.currentTimeMillis() < immunityTimeEnd) {
                    event.setCancelled(true);
                } else {
                    fallDamageImmunity.remove(player.getUniqueId());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        fallDamageImmunity.remove(event.getPlayer().getUniqueId());
    }
}