package com.stellinova.blitz.hud;

import com.stellinova.blitz.bridge.BlitzAccessBridge;
import com.stellinova.blitz.bridge.BlitzEvoBridge;
import com.stellinova.blitz.manager.BlitzManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
 * Blitz HUD — mirrors Bloodwell / SyncBlade / Winder pattern:
 *  - Loop every 10 ticks
 *  - Hides only if our "blitzhud" owns SIDEBAR
 *  - EVO + per-ability bonus % + cooldown seconds
 */
public class ScoreboardHud {

    private final org.bukkit.plugin.Plugin plugin;
    private final BlitzManager manager;
    @SuppressWarnings("unused")
    private final BlitzEvoBridge evo;

    private BukkitTask loop;

    public ScoreboardHud(org.bukkit.plugin.Plugin plugin, BlitzManager manager, BlitzEvoBridge evo) {
        this.plugin = plugin;
        this.manager = manager;
        this.evo = evo;
        startLoop();
    }

    public void shutdown() {
        try {
            if (loop != null) {
                loop.cancel();
                loop = null;
            }
        } catch (Throwable ignored) {}
    }

    private void startLoop() {
        shutdown();
        loop = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        refresh(p);
                    } catch (Throwable ignored) {}
                }
            }
        }.runTaskTimer(plugin, 1L, 10L); // ~0.5s, same as Winder/Sync
    }

    public void refresh(Player p) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;

        // Hide HUD if they can't use Blitz
        if (!BlitzAccessBridge.hasUsePerm(p) || !BlitzAccessBridge.hasBlitzRune(p)) {
            Scoreboard current = p.getScoreboard();
            Objective existing = current.getObjective("blitzhud");
            if (existing != null && existing.getDisplaySlot() == DisplaySlot.SIDEBAR) {
                Scoreboard empty = sm.getNewScoreboard();
                p.setScoreboard(empty);
            }
            return;
        }

        Scoreboard sb = sm.getNewScoreboard();
        Objective obj = sb.registerNewObjective(
                "blitzhud",
                Criteria.DUMMY,
                ChatColor.AQUA + "Blitz"
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        long now = System.currentTimeMillis();
        int evoLvl = Math.max(0, Math.min(3, evo.stage(p)));

        // Remaining cooldowns in ms
        long boltLeft    = Math.max(0L, manager.cdRemaining(p, "bolt"));
        long flashLeft   = Math.max(0L, manager.cdRemaining(p, "flash"));
        long shockLeft   = Math.max(0L, manager.cdRemaining(p, "shock"));
        long counterLeft = Math.max(0L, manager.cdRemaining(p, "counter"));
        long ultLeft     = Math.max(0L, manager.cdRemaining(p, "ult"));

        int score = 12;

        // EVO line
        add(obj, ChatColor.LIGHT_PURPLE + "EVO: " + ChatColor.AQUA + evoLvl, score--);

        // Bolt
        add(obj, abilityHeader("Bolt", boltLeft), score--);
        add(obj, statLine("Power", percent(abilityBonusPct(evoLvl, "bolt"))), score--);

        // Flash Step
        add(obj, abilityHeader("Flash Step", flashLeft), score--);
        add(obj, statLine("Step", percent(abilityBonusPct(evoLvl, "flash"))), score--);

        // Shock
        add(obj, abilityHeader("Shock", shockLeft), score--);
        add(obj, statLine("Stun", percent(abilityBonusPct(evoLvl, "shock"))), score--);

        // Counter Attack
        add(obj, abilityHeader("Counter", counterLeft), score--);
        add(obj, statLine("Reflect", percent(abilityBonusPct(evoLvl, "counter"))), score--);

        // spacer
        add(obj, ChatColor.GRAY + "", score--);

        // Sparking Rush (Ult)
        add(obj, ultLine(evoLvl, ultLeft), score--);

        p.setScoreboard(sb);
    }

    // ---------- helpers ----------

    private static void add(Objective obj, String text, int score) {
        String line = trim(text);
        while (obj.getScoreboard().getEntries().contains(line)) {
            line += ChatColor.RESET;
        }
        obj.getScore(line).setScore(score);
    }

    /** Ability name aqua when ready, red when on cooldown; shows yellow seconds while cooling. */
    private static String abilityHeader(String name, long msLeft) {
        if (msLeft <= 0L) {
            return ChatColor.AQUA + name;
        }
        int sec = (int) Math.ceil(msLeft / 1000.0);
        return ChatColor.RED + name + ChatColor.GRAY + " (" + ChatColor.YELLOW + sec + "s" + ChatColor.GRAY + ")";
    }

    private static String statLine(String label, String value) {
        return ChatColor.AQUA + "  " + label + ChatColor.WHITE + ": " + value;
    }

    private static String ultLine(int evoLvl, long msLeft) {
        if (evoLvl < 3) {
            return ChatColor.GOLD + "Sparking Rush" + ChatColor.WHITE + ": " + ChatColor.RED + "Locked";
        }
        if (msLeft <= 0L) {
            return ChatColor.GOLD + "Sparking Rush" + ChatColor.WHITE + ": " + ChatColor.GREEN + "Ready";
        }
        int sec = (int) Math.ceil(msLeft / 1000.0);
        return ChatColor.RED + "Sparking Rush" + ChatColor.WHITE + ": " + ChatColor.YELLOW + sec + "s";
    }

    /** Returns +percent value as text, e.g., +35%. Input expects fractional (0.35). */
    private static String percent(double frac) {
        int pct = (int) Math.round(frac * 100.0);
        return (pct >= 0 ? "+" : "") + pct + "%";
    }

    /**
     * Evo 0..3 => factors 0..3, with per-ability visual scaling.
     * This is visual only; real numbers come from EvoCore + config.
     */
    private static double abilityBonusPct(int evo, String key) {
        int factor = switch (evo) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            default -> 0;
        };

        String k = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);

        double bonus = switch (k) {
            case "bolt"    -> 0.30 * factor; // main nuke
            case "flash"   -> 0.25 * factor; // mobility
            case "shock"   -> 0.20 * factor; // control
            case "counter" -> 0.25 * factor; // reflect window
            case "ult"     -> 0.40 * factor; // Sparking Rush
            default -> 0.0;
        };

        return bonus; // fractional, e.g., 0.30
    }

    private static String trim(String s) {
        if (s == null) return "";
        return s.length() <= 40 ? s : s.substring(0, 40);
    }
}
