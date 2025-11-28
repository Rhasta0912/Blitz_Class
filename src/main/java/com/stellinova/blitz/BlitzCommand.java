package com.stellinova.blitz.command;

import com.stellinova.blitz.bridge.BlitzAccessBridge;
import com.stellinova.blitz.bridge.BlitzEvoBridge;
import com.stellinova.blitz.core.BlitzPlugin;
import com.stellinova.blitz.hud.ScoreboardHud;
import com.stellinova.blitz.manager.BlitzManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BlitzCommand implements CommandExecutor {

    private final BlitzPlugin plugin;
    private final BlitzManager manager;
    private final ScoreboardHud hud;
    private final BlitzEvoBridge evo;

    public BlitzCommand(BlitzPlugin plugin, BlitzManager manager, ScoreboardHud hud, BlitzEvoBridge evo) {
        this.plugin = plugin;
        this.manager = manager;
        this.hud = hud;
        this.evo = evo;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage("§bBlitz §7» §fUsage: /" + label + " <bolt|flash|shock|counter|ult|status|reload|admin|reset>");
            return true;
        }

        String sub = args[0].toLowerCase();

        // STATUS
        if (sub.equals("status")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            int lvl = evo.stage(p);
            boolean has = BlitzAccessBridge.hasBlitzRune(p);
            sender.sendMessage("§bBlitz Status");
            sender.sendMessage("§7 Rune: " + (has ? "§bBlitz" : "§8None"));
            sender.sendMessage("§7 Evo: §b" + lvl);
            sender.sendMessage("§7 Bolt: §a" + manager.cooldownText(p, "bolt"));
            sender.sendMessage("§7 Flash: §a" + manager.cooldownText(p, "flash"));
            sender.sendMessage("§7 Shock: §a" + manager.cooldownText(p, "shock"));
            sender.sendMessage("§7 Counter: §a" + manager.cooldownText(p, "counter"));
            sender.sendMessage("§7 Ult: §a" + manager.cooldownText(p, "ult"));
            return true;
        }

        // RELOAD
        if (sub.equals("reload")) {
            if (!sender.hasPermission("blitz.admin")) {
                sender.sendMessage("§cYou are not an admin.");
                return true;
            }
            plugin.getBlitzConfig().reload();
            for (Player p : Bukkit.getOnlinePlayers()) {
                hud.refresh(p);
            }
            sender.sendMessage("§aBlitz config reloaded.");
            return true;
        }

        // ADMIN MODE TOGGLE
        if (sub.equals("admin")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (!BlitzAccessBridge.isAdmin(p)) {
                p.sendMessage("§cYou are not an admin.");
                return true;
            }
            boolean enable = true; // Default to toggle ON if no arg, or check arg
            if (args.length > 1) {
                String arg = args[1].toLowerCase();
                if (arg.equals("off")) enable = false;
                else if (arg.equals("on")) enable = true;
                else {
                    p.sendMessage("§cUsage: /blitz admin <on|off>");
                    return true;
                }
            } else {
                // Toggle behavior if no arg provided? Or force usage?
                // Let's default to ON for convenience, or check current state?
                // Current state check isn't exposed easily here without getter.
                // Let's assume /blitz admin -> ON for now as per previous behavior.
            }

            manager.setAdminMode(p, enable);
            if (enable) {
                p.sendMessage("§aBlitz admin mode enabled. Cooldowns and hunger costs are ignored.");
            } else {
                p.sendMessage("§cBlitz admin mode disabled.");
            }
            return true;
        }

        // RESET – clean up Blitz state (called by RuneSelector when switching away)
        if (sub.equals("reset")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only");
                return true;
            }
            
            // Clean up state silently (RuneSelector calling us)
            manager.warm(p);
            hud.hide(p);
            return true;
        }

        // RUNE – activate or remove Blitz rune
        if (sub.equals("rune")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only");
                return true;
            }
            
            // Check if they already have the rune (activating) or want to remove it
            boolean hasBlitz = BlitzAccessBridge.hasBlitzRune(p);
            
            if (hasBlitz) {
                // Already have it - user wants to remove it
                try {
                    Class<?> runeServiceClass = Class.forName("com.stellinova.runeselector.api.IRuneService");
                    org.bukkit.plugin.RegisteredServiceProvider<?> rsp = 
                        Bukkit.getServicesManager().getRegistration(runeServiceClass);
                        
                    if (rsp != null) {
                        Object runeService = rsp.getProvider();
                        Class<?> runeTypeClass = Class.forName("com.stellinova.runeselector.api.RuneType");
                        Object noneRune = runeTypeClass.getField("NONE").get(null);
                        
                        runeService.getClass()
                            .getMethod("setActiveRune", org.bukkit.entity.Player.class, runeTypeClass)
                            .invoke(runeService, p, noneRune);
                            
                        p.sendMessage("§aBlitz rune removed! Use /rune to select another class.");
                    } else {
                        p.sendMessage("§cRuneSelector not found.");
                    }
                } catch (Exception e) {
                    p.sendMessage("§cFailed to remove rune: " + e.getMessage());
                }
            } else {
                // Don't have it - RuneSelector is activating it
                p.sendMessage("§bBlitz rune activated! Use Sneak for abilities.");
            }
            return true;
        }

        // Ability subcommands (optional, triggers cover main gameplay)
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!BlitzAccessBridge.hasUsePerm(p)) {
            p.sendMessage("§cYou do not have permission to use Blitz.");
            return true;
        }

        if (!BlitzAccessBridge.hasBlitzRune(p)) {
            p.sendMessage("§cYou do not have the Blitz rune active.");
            return true;
        }

        switch (sub) {
            case "bolt":
                manager.castBolt(p);
                break;
            case "flash":
            case "flashstep":
                manager.castFlash(p);
                break;
            case "shock":
                manager.startShockCharge(p);
                break;
            case "counter":
            case "counterattack":
                manager.castCounter(p);
                break;
            case "ult":
            case "ultimate":
            case "rush":
                manager.castUlt(p);
                break;
            default:
                p.sendMessage("§bBlitz §7» §fUsage: /" + label + " <bolt|flash|shock|counter|ult|status|reload|admin|reset>");
                break;
        }
        return true;
    }
}
