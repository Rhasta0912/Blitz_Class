package com.stellinova.blitz.listener;

import com.stellinova.blitz.manager.BlitzManager;
import org.bukkit.Location;
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

        // Prevent stunned players attacking
        if (attacker instanceof Player) {
            Player p = (Player) attacker;
            if (manager.isStunned(p)) {
                e.setCancelled(true);
                p.sendMessage("§bBlitz §7» §cYou are stunned.");
                return;
            }
        }

        // Handle Counter reflect
        if (victim instanceof Player && attacker instanceof LivingEntity) {
            Player victimPlayer = (Player) victim;
            LivingEntity attackEntity = (LivingEntity) attacker;

            boolean reflected = manager.handleCounterHit(victimPlayer, attackEntity, e);
            if (reflected) return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!manager.isStunned(p)) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        // Allow falling but block moving up or horizontally
        boolean goingDown = to.getY() < from.getY();

        if (goingDown) {
            Location fixed = to.clone();
            fixed.setX(from.getX());
            fixed.setZ(from.getZ());
            e.setTo(fixed);
        } else {
            e.setTo(from);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.handleQuit(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        manager.handleSneak(e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        manager.handleSwap(e);
    }
}
