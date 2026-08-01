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
import org.bukkit.event.block.BlockPlaceEvent;
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

    private boolean consumableEnabled;
    private Material consumableMaterial;
    private double consumableVelocityMultiplier;
    private int consumableNoDamageTicks;
    private boolean consumableWgEnabled;
    private final Set<String> consumableAllowedRegions = new HashSet<>();

    public BounceListener(BetterBouncyBlocks plugin) {
        this.plugin = plugin;
        this.isWorldGuardPresent = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    public void reloadValues() {
        String configBlockName = plugin.getConfig().getString("block", "SLIME_BLOCK").toUpperCase();
        this.targetMaterial = Material.matchMaterial(configBlockName);
        this.velocityMultiplier = plugin.getConfig().getDouble("velocity-multiplier", 2.0);
        this.noDamageTicks = plugin.getConfig().getInt("no-damage-ticks", 0);
        this.wgEnabled = plugin.getConfig().getBoolean("worldguard.enabled", false);

        this.allowedRegions.clear();
        for (String region : plugin.getConfig().getStringList("worldguard.regions")) {
            this.allowedRegions.add(region.toLowerCase());
        }

        this.consumableEnabled = plugin.getConfig().getBoolean("consumable-block.enabled", true);
        String consumableBlockName = plugin.getConfig().getString("consumable-block.block", "MAGENTA_GLAZED_TERRACOTTA").toUpperCase();
        this.consumableMaterial = Material.matchMaterial(consumableBlockName);
        this.consumableVelocityMultiplier = plugin.getConfig().getDouble("consumable-block.velocity-multiplier", 1.5);
        this.consumableNoDamageTicks = plugin.getConfig().getInt("consumable-block.no-damage-ticks", 40);
        this.consumableWgEnabled = plugin.getConfig().getBoolean("consumable-block.worldguard.enabled", false);

        this.consumableAllowedRegions.clear();
        for (String region : plugin.getConfig().getStringList("consumable-block.worldguard.regions")) {
            this.consumableAllowedRegions.add(region.toLowerCase());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;

        Player player = event.getPlayer();
        Block blockBelow = event.getTo().getBlock().getRelative(BlockFace.DOWN);
        Material blockType = blockBelow.getType();

        if (targetMaterial != null && blockType == targetMaterial) {
            if (!player.hasPermission("betterbouncyblocks.use")) return;
            if (!isInAllowedRegion(blockBelow.getLocation(), wgEnabled, allowedRegions)) return;

            applyBounce(player, velocityMultiplier, noDamageTicks);
            return;
        }

        if (consumableEnabled && consumableMaterial != null && blockType == consumableMaterial) {
            if (!player.hasPermission("betterbouncyblocks.use.consumable")) return;
            if (!isInAllowedRegion(blockBelow.getLocation(), consumableWgEnabled, consumableAllowedRegions)) return;

            applyBounce(player, consumableVelocityMultiplier, consumableNoDamageTicks);
            blockBelow.setType(Material.AIR);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block placedBlock = event.getBlockPlaced();

        Location playerLoc = player.getLocation();
        Block blockBelow = playerLoc.getBlock().getRelative(BlockFace.DOWN);
        Block blockAtFeet = playerLoc.getBlock();

        if (!placedBlock.equals(blockBelow) && !placedBlock.equals(blockAtFeet)) {
            return;
        }

        Material blockType = placedBlock.getType();

        if (targetMaterial != null && blockType == targetMaterial) {
            if (!player.hasPermission("betterbouncyblocks.use")) return;
            if (!isInAllowedRegion(placedBlock.getLocation(), wgEnabled, allowedRegions)) return;

            applyBounce(player, velocityMultiplier, noDamageTicks);
            return;
        }

        if (consumableEnabled && consumableMaterial != null && blockType == consumableMaterial) {
            if (!player.hasPermission("betterbouncyblocks.use.consumable")) return;
            if (!isInAllowedRegion(placedBlock.getLocation(), consumableWgEnabled, consumableAllowedRegions)) return;

            applyBounce(player, consumableVelocityMultiplier, consumableNoDamageTicks);
            placedBlock.setType(Material.AIR);
        }
    }

    private void applyBounce(Player player, double multiplier, int damageTicks) {
        player.setVelocity(new Vector(player.getVelocity().getX(), multiplier, player.getVelocity().getZ()));

        if (damageTicks > 0) {
            long immunityTimeEnd = System.currentTimeMillis() + (damageTicks * 50L);
            fallDamageImmunity.put(player.getUniqueId(), immunityTimeEnd);
        }
    }

    private boolean isInAllowedRegion(Location loc, boolean regionCheckEnabled, Set<String> regions) {
        if (!isWorldGuardPresent || !regionCheckEnabled) {
            return true;
        }

        if (regions.isEmpty()) {
            return false;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

        for (ProtectedRegion region : set) {
            if (regions.contains(region.getId().toLowerCase())) {
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