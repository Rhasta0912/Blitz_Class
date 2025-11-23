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
            sender.sendMessage("§bBlitz §7» §fUsage: /" + label + " <bolt|flash|shock|counter|ult|status|reload|admin>");
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
            boolean disable = args.length > 1 && args[1].equalsIgnoreCase("off");
            manager.setAdminMode(p, !disable);
            if (!disable) {
                p.sendMessage("§aBlitz admin mode enabled. Cooldowns are ignored.");
            } else {
                p.sendMessage("§cBlitz admin mode disabled.");
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
                manager.castShock(p);
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
                p.sendMessage("§bBlitz §7» §fUsage: /" + label + " <bolt|flash|shock|counter|ult|status|reload|admin>");
                break;
        }
        return true;
    }
}
