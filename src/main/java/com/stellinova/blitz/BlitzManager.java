package com.stellinova.blitz.manager;

import com.stellinova.blitz.bridge.BlitzAccessBridge;
import com.stellinova.blitz.bridge.BlitzEvoBridge;
import com.stellinova.blitz.core.BlitzConfig;
import com.stellinova.blitz.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BlitzManager {

    private final Plugin plugin;
    private final BlitzEvoBridge evo;
    private final BlitzConfig cfg;

    // double-tap detection for inputs
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private final Map<UUID, Long> lastSwap = new HashMap<>();
    private static final long DOUBLE_TAP_MS = 250L;

    // admin mode (ignores cooldowns / costs)
    private final Set<UUID> adminPlayers = new HashSet<>();

    // sneak-held players for Flash preview
    private final Set<UUID> sneakingPlayers = new HashSet<>();

    // pending single-tap Bolt (so Flash double-tap doesn’t auto-fire Bolt first)
    private final Set<UUID> pendingBolt = new HashSet<>();

    // Shock charge state
    private final Set<UUID> shockCharging = new HashSet<>();
    private final Map<UUID, Long> shockChargeStart = new HashMap<>();
    private static final long SHOCK_CHARGE_MS = 1500L;

    private BukkitTask previewTask;

    public BlitzManager(Plugin plugin, BlitzEvoBridge evo, BlitzConfig cfg) {
        this.plugin = plugin;
        this.evo = evo;
        this.cfg = cfg;
        startPreviewLoop();
    }

    public PlayerData data(Player p) {
        return PlayerData.get(p);
    }

    public void warm(Player p) {
        data(p).reset();
        UUID id = p.getUniqueId();
        lastSneak.remove(id);
        lastSwap.remove(id);
        sneakingPlayers.remove(id);
        pendingBolt.remove(id);
        shockCharging.remove(id);
        shockChargeStart.remove(id);
        if (previewTask != null && !previewTask.isCancelled()) {
            // If we were previewing for this player, it might be tricky to stop just for them without a map, 
            // but usually preview is global or per-player task. 
            // The current code has a single 'previewTask' loop, so we don't cancel it.
        }
    }

    public void handleQuit(Player p) {
        PlayerData.remove(p);
        UUID id = p.getUniqueId();
        lastSneak.remove(id);
        lastSwap.remove(id);
        adminPlayers.remove(id);
        sneakingPlayers.remove(id);
        pendingBolt.remove(id);
        shockCharging.remove(id);
        shockChargeStart.remove(id);
    }

    public void shutdown() {
        try {
            if (previewTask != null) {
                previewTask.cancel();
                previewTask = null;
            }
        } catch (Throwable ignored) {}
    }

    // ===========================
    //  ADMIN MODE
    // ===========================

    public void setAdminMode(Player p, boolean enabled) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        if (enabled) {
            adminPlayers.add(id);
        } else {
            adminPlayers.remove(id);
        }
    }

    public boolean isAdminMode(Player p) {
        return p != null && adminPlayers.contains(p.getUniqueId());
    }

    // ===========================
    //  STATE
    // ===========================

    public boolean isStunned(Player p) {
        return System.currentTimeMillis() < data(p).getStunnedUntil();
    }

    public boolean isCounterActive(Player p) {
        return System.currentTimeMillis() < data(p).getCounterUntil();
    }

    public boolean isUltActive(Player p) {
        return System.currentTimeMillis() < data(p).getBlitzRushUntil();
    }

    private void applyStunEntity(LivingEntity ent, long durationMs) {
        if (ent instanceof Player) {
            Player p = (Player) ent;
            PlayerData pd = data(p);
            pd.setStunnedUntil(System.currentTimeMillis() + durationMs);
            int ticks = (int) (durationMs / 50L);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 6, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, ticks, 128, false, false, true));
            p.sendMessage("§bBlitz §7» §cYou are stunned!");

            // Removed forced teleport to ground to prevent anti-cheat kicks while stunned in air
        } else {
            int ticks = (int) (durationMs / 50L);
            ent.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 5, false, false, true));
        }
    }

    /**
     * Counter logic:
     * - If window active, victim takes 0.
     * - All incoming damage is reflected to attacker.
     */
    public boolean handleCounterHit(Player victim, LivingEntity attacker, EntityDamageByEntityEvent e) {
        if (!isCounterActive(victim)) return false;

        double dmg = e.getDamage(); // Reflect raw damage, not final
        e.setCancelled(true); // victim takes no damage

        attacker.damage(dmg, victim);

        victim.sendMessage("§bBlitz §7» §fYou reflected §c" +
                String.format(java.util.Locale.US, "%.1f", dmg) + "§f damage!");
        if (attacker instanceof Player) {
            ((Player) attacker).sendMessage("§bBlitz §7» §fYour attack was §creflected§f!");
        }

        data(victim).setCounterUntil(0L); // one-shot window per activation
        return true;
    }

    // ===========================
    //  COOLDOWNS
    // ===========================

    public boolean onCd(Player p, String key) {
        if (isAdminMode(p)) return false; // admin = no cooldown
        PlayerData pd = data(p);
        long now = System.currentTimeMillis();
        if ("bolt".equals(key)) return now < pd.getBoltReadyAt();
        if ("flash".equals(key)) return now < pd.getFlashReadyAt();
        if ("shock".equals(key)) return now < pd.getShockReadyAt();
        if ("counter".equals(key)) return now < pd.getCounterReadyAt();
        if ("ult".equals(key)) return now < pd.getUltReadyAt();
        return false;
    }

    public long cdRemaining(Player p, String key) {
        if (isAdminMode(p)) return 0L;
        PlayerData pd = data(p);
        long now = System.currentTimeMillis();
        long at = 0L;
        if ("bolt".equals(key)) at = pd.getBoltReadyAt();
        else if ("flash".equals(key)) at = pd.getFlashReadyAt();
        else if ("shock".equals(key)) at = pd.getShockReadyAt();
        else if ("counter".equals(key)) at = pd.getCounterReadyAt();
        else if ("ult".equals(key)) at = pd.getUltReadyAt();
        return Math.max(0L, at - now);
    }

    public String cooldownText(Player p, String key) {
        if (!onCd(p, key)) return "ready";
        long sec = cdRemaining(p, key) / 1000L;
        return sec + "s";
    }

    private void setCd(Player p, String key) {
        if (isAdminMode(p)) return; // do not even set cooldowns in admin
        PlayerData pd = data(p);
        long t = System.currentTimeMillis() + cfg.cooldown(key);
        if ("bolt".equals(key)) pd.setBoltReadyAt(t);
        else if ("flash".equals(key)) pd.setFlashReadyAt(t);
        else if ("shock".equals(key)) pd.setShockReadyAt(t);
        else if ("counter".equals(key)) pd.setCounterReadyAt(t);
        else if ("ult".equals(key)) pd.setUltReadyAt(t);
    }

    private void msg(Player p, String t) {
        p.sendMessage("§bBlitz §7» §f" + t);
    }

    // ===========================
    //  HUNGER COSTS
    // ===========================

    /**
     * Basic hunger gate: returns false if not enough food.
     * Admin mode: no cost, always true.
     */
    private boolean requireHunger(Player p, int cost) {
        if (isAdminMode(p)) return true;
        int food = p.getFoodLevel();
        if (food < cost) {
            msg(p, "You are too exhausted to use this.");
            return false;
        }
        p.setFoodLevel(Math.max(0, food - cost));
        return true;
    }

    // ===========================
    //  FLASH PREVIEW LOOP
    // ===========================

    private void startPreviewLoop() {
        try {
            if (previewTask != null) {
                previewTask.cancel();
            }
        } catch (Throwable ignored) {}

        previewTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (sneakingPlayers.isEmpty()) return;

                for (UUID id : new HashSet<>(sneakingPlayers)) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null || !p.isOnline() || !p.isSneaking()) {
                        sneakingPlayers.remove(id);
                        continue;
                    }
                    if (!BlitzAccessBridge.hasUsePerm(p) || !BlitzAccessBridge.hasBlitzRune(p)) continue;
                    if (isStunned(p)) continue;
                    if (onCd(p, "flash")) continue;

                    // compute same target as Flash Step
                    Location start = p.getLocation();
                    Vector dir = start.getDirection().normalize();
                    World w = p.getWorld();

                    int stage = evo.stage(p);
                    double maxDist = evo.scalar(p, "flash_distance", 1.0) * (6 + (stage) * 2);

                    Location safe = start.clone();
                    for (double d = 0.4; d <= maxDist; d += 0.4) {
                        Location test = start.clone().add(dir.clone().multiply(d));
                        if (!isSafe(test)) break;
                        safe = test;
                    }

                    // preview particles at target
                    w.spawnParticle(
                            Particle.ELECTRIC_SPARK,
                            safe.clone().add(0, 0.2, 0),
                            10,
                            0.25, 0.35, 0.25,
                            0.01
                    );
                }
            }
        }.runTaskTimer(plugin, 5L, 5L); // ~0.25s
    }

    // ===========================
    //  INPUT TRIGGERS
    // ===========================

    /**
     * Sneak tap input:
     *  - Single tap  -> Bolt  (delayed by DOUBLE_TAP_MS)
     *  - Double tap  -> Flash Step
     *
     * Holding sneak keeps Flash preview active.
     * Double-tap no longer forces Bolt first.
     */
    public void handleSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!BlitzAccessBridge.hasUsePerm(p) || !BlitzAccessBridge.hasBlitzRune(p)) return;

        UUID id = p.getUniqueId();

        if (e.isSneaking()) {
            // pressed sneak: track for preview
            sneakingPlayers.add(id);

            long now = System.currentTimeMillis();
            long last = lastSneak.getOrDefault(id, 0L);
            lastSneak.put(id, now);

            if (now - last <= DOUBLE_TAP_MS) {
                // double tap -> Flash, cancel pending Bolt
                pendingBolt.remove(id);
                castFlash(p);
            } else {
                // schedule possible single-tap Bolt
                pendingBolt.add(id);
                long scheduleTicks = Math.max(1L, DOUBLE_TAP_MS / 50L);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // if still pending and no double-tap happened
                    if (!pendingBolt.remove(id)) return;
                    if (!p.isOnline()) return;
                    // Only cast Bolt if they still have Blitz + perms
                    if (!BlitzAccessBridge.hasUsePerm(p) || !BlitzAccessBridge.hasBlitzRune(p)) return;
                    castBolt(p);
                }, scheduleTicks);
            }
        } else {
            // released sneak: stop preview
            sneakingPlayers.remove(id);
        }
    }

    /**
     * Swap-hand (F) input:
     *  - Double tap -> Ultimate
     *  - Single tap -> Shock (standing) or Counter (while sneaking)
     */
    public void handleSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!BlitzAccessBridge.hasUsePerm(p) || !BlitzAccessBridge.hasBlitzRune(p)) return;

        e.setCancelled(true); // do not actually swap items

        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        long last = lastSwap.getOrDefault(id, 0L);
        lastSwap.put(id, now);

        boolean doubleTap = now - last <= DOUBLE_TAP_MS;

        if (doubleTap) {
            castUlt(p);
            return;
        }

        if (p.isSneaking()) {
            // Shift + F -> Shock (Charge Stun)
            startShockCharge(p);
        } else {
            // F -> Counter
            castCounter(p);
        }
    }

    // ===========================
    //  ABILITIES
    // ===========================

    public void castBolt(Player p) {
        if (!BlitzAccessBridge.hasUsePerm(p) || !BlitzAccessBridge.hasBlitzRune(p)) return;

        if (onCd(p, "bolt")) {
            long sec = cdRemaining(p, "bolt") / 1000L;
            msg(p, "Bolt on cooldown (" + sec + "s)");
            return;
        }

        if (isStunned(p)) {
            msg(p, "You are stunned.");
            return;
        }

        if (!requireHunger(p, 2)) { // HUNGER COST
            return;
        }

        int stage = evo.stage(p);
        double dmg = evo.scalar(p, "bolt_damage", 1.0) * (5 + stage * 2);

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World w = p.getWorld();

        RayTraceResult result = w.rayTraceEntities(
                eye, dir, 30, 1.4,
                e -> e instanceof LivingEntity && e != p);

        Location target =
                (result != null && result.getHitEntity() != null)
                        ? result.getHitEntity().getLocation()
                        : eye.clone().add(dir.multiply(8));

        w.strikeLightningEffect(target);

        for (Entity e : w.getNearbyEntities(target, 2.6, 2.6, 2.6)) {
            if (e instanceof LivingEntity && e != p) {
                ((LivingEntity) e).damage(dmg, p);
            }
        }

        w.spawnParticle(Particle.ELECTRIC_SPARK, target, 40, .6, .8, .6, .08);
        w.playSound(target, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.2f);

        setCd(p, "bolt");
        msg(p, "Bolt cast!");
    }

    public void castFlash(Player p) {
        if (!BlitzAccessBridge.hasUsePerm(p) || !BlitzAccessBridge.hasBlitzRune(p)) return;

        if (onCd(p, "flash")) {
            long sec = cdRemaining(p, "flash") / 1000L;
            msg(p, "Flash Step on cooldown (" + sec + "s)");
            return;
        }

        if (isStunned(p)) {
            msg(p, "You are stunned.");
            return;
        }

        if (!requireHunger(p, 3)) { // higher cost for mobility
            return;
        }

        int stage = evo.stage(p);
        double maxDist = evo.scalar(p, "flash_distance", 1.0) * (6 + (stage) * 2);

        Location start = p.getLocation();
        Vector dir = start.getDirection().normalize();
        World w = p.getWorld();
        Location safe = start.clone();

        for (double d = 0.4; d <= maxDist; d += 0.4) {
            Location test = start.clone().add(dir.clone().multiply(d));
            if (!isSafe(test)) break;
            safe = test;
        }

        w.spawnParticle(Particle.ELECTRIC_SPARK, start, 20, .5, 1.0, .5, 0.1);
        p.teleport(safe);
        w.spawnParticle(Particle.ELECTRIC_SPARK, safe, 20, .5, 1.0, .5, 0.1);
        w.playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.6f);

        setCd(p, "flash");
        msg(p, "Flash Step!");
    }

    private boolean isSafe(Location loc) {
        Location feet = loc.clone();
        Location head = loc.clone().add(0, 1, 0);
        return feet.getBlock().isPassable() && head.getBlock().isPassable();
    }

    public void startShockCharge(Player p) {
        if (!BlitzAccessBridge.hasUsePerm(p) || !BlitzAccessBridge.hasBlitzRune(p)) return;

        if (onCd(p, "shock")) {
            long sec = cdRemaining(p, "shock") / 1000L;
            msg(p, "Shock on cooldown (" + sec + "s)");
            return;
        }

        if (shockCharging.contains(p.getUniqueId())) return;

        shockCharging.add(p.getUniqueId());
        shockChargeStart.put(p.getUniqueId(), System.currentTimeMillis());
        msg(p, "§bCharging Shock...");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.0f, 2.0f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || !shockCharging.contains(p.getUniqueId())) {
                    cancel();
                    return;
                }

                long elapsed = System.currentTimeMillis() - shockChargeStart.get(p.getUniqueId());
                if (elapsed >= SHOCK_CHARGE_MS) {
                    shockCharging.remove(p.getUniqueId());
                    shockChargeStart.remove(p.getUniqueId());
                    fireShockProjectile(p);
                    cancel();
                    return;
                }

                // Charge particles
                Location loc = p.getLocation().add(0, 1, 0);
                double progress = (double) elapsed / SHOCK_CHARGE_MS;
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 5, 0.5 * progress, 0.5 * progress, 0.5 * progress, 0.05);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void fireShockProjectile(Player p) {
        if (!requireHunger(p, 2)) return;

        Location start = p.getEyeLocation();
        Vector dir = start.getDirection().normalize().multiply(1.5); // Speed

        new BukkitRunnable() {
            Location current = start.clone();
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 40) { // Max range/time
                    cancel();
                    return;
                }

                current.add(dir);
                current.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, current, 5, 0.1, 0.1, 0.1, 0.02);

                // Hit detection
                for (Entity e : current.getWorld().getNearbyEntities(current, 0.8, 0.8, 0.8)) {
                    if (e instanceof LivingEntity && e != p) {
                        LivingEntity target = (LivingEntity) e;
                        int stage = evo.stage(p);
                        long durationMs = (long) (1000L * (1 + stage) * evo.scalar(p, "shock_duration", 1.0));
                        
                        applyStunEntity(target, durationMs);
                        current.getWorld().playSound(current, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.0f, 1.4f);
                        current.getWorld().spawnParticle(Particle.FLASH, current, 1);
                        
                        msg(p, "§bStunned " + target.getName() + "!");
                        setCd(p, "shock");
                        cancel();
                        return;
                    }
                }

                if (current.getBlock().getType().isSolid()) {
                    current.getWorld().spawnParticle(Particle.SMOKE_NORMAL, current, 5, 0.1, 0.1, 0.1, 0.01);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        p.getWorld().playSound(start, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 2.0f);
        msg(p, "§bShock fired!");
    }

    public void castCounter(Player p) {
        if (!BlitzAccessBridge.hasUsePerm(p) || !BlitzAccessBridge.hasBlitzRune(p)) return;

        if (onCd(p, "counter")) {
            long sec = cdRemaining(p, "counter") / 1000L;
            msg(p, "Counter on cooldown (" + sec + "s)");
            return;
        }

        if (isStunned(p)) {
            msg(p, "You are stunned.");
            return;
        }

        // VERY hunger costly for counter
        if (!requireHunger(p, 4)) {
            return;
        }

        // 2.5s invincibility / reflect window
        long durationMs = 2500L;

        int ticks = (int) (durationMs / 50L);
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.ABSORPTION, ticks, 1, false, false, true));
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.DAMAGE_RESISTANCE, ticks, 2, false, false, true));

        data(p).setCounterUntil(System.currentTimeMillis() + durationMs);

        Location loc = p.getLocation().add(0, 1, 0);
        World w = p.getWorld();
        w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 40, 0.5, 0.8, 0.5, 0.1);
        w.playSound(loc, Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.3f);

        setCd(p, "counter");
        msg(p, "Counter ready for 2.5 seconds.");
    }

    public void castUlt(Player p) {
        if (!BlitzAccessBridge.hasUsePerm(p) || !BlitzAccessBridge.hasBlitzRune(p)) return;

        if (onCd(p, "ult")) {
            long sec = cdRemaining(p, "ult") / 1000L;
            msg(p, "Blitz Rush on cooldown (" + sec + "s)");
            return;
        }

        if (isStunned(p)) {
            msg(p, "You are stunned.");
            return;
        }

        if (!requireHunger(p, 4)) { // big cost ult
            return;
        }

        int durationTicks = 200;
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, durationTicks, 1, false, false, true));
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.INCREASE_DAMAGE, durationTicks, 1, false, false, true));

        long stunMs = 2000L;
        double radius = 10.0;
        Location center = p.getLocation();
        World w = p.getWorld();

        for (Entity e : w.getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity && e != p) {
                // stun them
                applyStunEntity((LivingEntity) e, stunMs);
                // lightning effect on each target
                w.strikeLightningEffect(e.getLocation());
            }
        }

        data(p).setBlitzRushUntil(System.currentTimeMillis() + durationTicks * 50L);

        w.spawnParticle(Particle.ELECTRIC_SPARK, center, 90, 1.5, 1.0, 1.5, 0.16);
        w.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);

        setCd(p, "ult");
        msg(p, "§6Sparking Rush activated!");
    }
}
