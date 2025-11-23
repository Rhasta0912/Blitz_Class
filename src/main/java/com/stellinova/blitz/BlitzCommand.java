package com.stellinova.blitz.command;

import com.stellinova.blitz.bridge.BlitzAccessBridge;
import com.stellinova.blitz.bridge.BlitzEvoBridge;
import com.stellinova.blitz.core.BlitzPlugin;
import com.stellinova.blitz.hud.ScoreboardHud;
import com.stellinova.blitz.manager.BlitzManager;
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
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Player p = (Player) sender;

        if (!BlitzAccessBridge.hasUsePerm(p)) {
            p.sendMessage("§cYou do not have permission to use Blitz.");
            return true;
        }

        if (!BlitzAccessBridge.hasBlitzRune(p)) {
            p.sendMessage("§cYou do not have the Blitz rune active.");
            return true;
        }

        if (args.length == 0) {
            p.sendMessage("§bBlitz §7» §fUsage: /" + label + " <bolt|flash|shock|counter|ult|status|reload>");
            return true;
        }

        String sub = args[0].toLowerCase();
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
            case "status":
                p.sendMessage("§bBlitz §7» §fEvo stage: §b" + evo.stage(p));
                p.sendMessage("§fBolt CD: §a" + manager.cooldownText(p, "bolt"));
                p.sendMessage("§fFlash CD: §a" + manager.cooldownText(p, "flash"));
                p.sendMessage("§fShock CD: §a" + manager.cooldownText(p, "shock"));
                p.sendMessage("§fCounter CD: §a" + manager.cooldownText(p, "counter"));
                p.sendMessage("§fUlt CD: §a" + manager.cooldownText(p, "ult"));
                break;
            case "reload":
                if (!BlitzAccessBridge.isAdmin(p)) {
                    p.sendMessage("§cYou are not an admin.");
                    break;
                }
                plugin.getBlitzConfig().reload();
                p.sendMessage("§aBlitz config reloaded.");
                break;
            default:
                p.sendMessage("§bBlitz §7» §fUnknown subcommand.");
                break;
        }
        return true;
    }
}
