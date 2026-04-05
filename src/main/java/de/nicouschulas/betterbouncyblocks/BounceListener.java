package de.nicouschulas.betterbouncyblocks;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BounceListener implements Listener {

    private final BetterBouncyBlocks plugin;
    private final Map<UUID, Long> fallDamageImmunity = new HashMap<>();

    public BounceListener(BetterBouncyBlocks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;

        Player player = event.getPlayer();
        Block blockBelow = event.getTo().getBlock().getRelative(BlockFace.DOWN);

        String configBlockName = plugin.getConfig().getString("Block", "SLIME_BLOCK").toUpperCase();
        Material targetMaterial = Material.matchMaterial(configBlockName);

        if (targetMaterial != null && blockBelow.getType() == targetMaterial) {
            double height = plugin.getConfig().getDouble("velocity-multiplier", 2.0);
            player.setVelocity(new Vector(player.getVelocity().getX(), height, player.getVelocity().getZ()));

            int noDamageTicks = plugin.getConfig().getInt("no-damage-ticks", 500);
            long immunityTimeEnd = System.currentTimeMillis() + (noDamageTicks * 50L);

            fallDamageImmunity.put(player.getUniqueId(), immunityTimeEnd);
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (fallDamageImmunity.containsKey(player.getUniqueId())) {
                if (System.currentTimeMillis() < fallDamageImmunity.get(player.getUniqueId())) {
                    event.setCancelled(true);
                } else {
                    fallDamageImmunity.remove(player.getUniqueId());
                }
            }
        }
    }
}