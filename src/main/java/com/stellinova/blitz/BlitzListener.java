package com.stellinova.blitz.listener;

import com.stellinova.blitz.manager.BlitzManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class BlitzListener implements Listener {

    private final BlitzManager manager;

    public BlitzListener(BlitzManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        Entity attacker = e.getDamager();

        if (attacker instanceof Player) {
            Player p = (Player) attacker;
            if (manager.isStunned(p)) {
                e.setCancelled(true);
                p.sendMessage("§bBlitz §7» §cYou are stunned.");
                return;
            }
        }

        if (victim instanceof Player && attacker instanceof LivingEntity) {
            manager.triggerCounterHit((Player) victim, (LivingEntity) attacker, e.getFinalDamage());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!manager.isStunned(p)) return;

        if (!e.getFrom().toVector().equals(e.getTo().toVector())) {
            e.setTo(e.getFrom());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.handleQuit(e.getPlayer());
    }

    // Sneak tap -> Bolt / Flash
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        manager.handleSneak(e);
    }

    // Swap-hand (F) -> Shock / Counter / Ult
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        manager.handleSwap(e);
    }
}
