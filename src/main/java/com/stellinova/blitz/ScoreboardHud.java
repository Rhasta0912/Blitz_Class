package com.stellinova.blitz.hud;

import com.stellinova.blitz.bridge.BlitzEvoBridge;
import com.stellinova.blitz.manager.BlitzManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScoreboardHud {

    private final Plugin plugin;
    private final BlitzManager manager;
    private final BlitzEvoBridge evo;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private int taskId = -1;

    public ScoreboardHud(Plugin plugin, BlitzManager manager, BlitzEvoBridge evo) {
        this.plugin = plugin;
        this.manager = manager;
        this.evo = evo;
        start();
    }

    private void start() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                refresh(p);
            }
        }, 40L, 40L);
    }

    public void refresh(Player p) {
        Scoreboard board = boards.computeIfAbsent(p.getUniqueId(),
                id -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective obj = board.getObjective("blitz");
        if (obj == null) {
            obj = board.registerNewObjective("blitz", "dummy", "§bBlitz");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        int evoStage = evo.stage(p);
        int line = 8;
        obj.getScore("§7----------------").setScore(line--);
        obj.getScore("§fEvo: §b" + evoStage).setScore(line--);
        obj.getScore("§fBolt: §a" + manager.cooldownText(p, "bolt")).setScore(line--);
        obj.getScore("§fFlash: §a" + manager.cooldownText(p, "flash")).setScore(line--);
        obj.getScore("§fShock: §a" + manager.cooldownText(p, "shock")).setScore(line--);
        obj.getScore("§fCounter: §a" + manager.cooldownText(p, "counter")).setScore(line--);
        obj.getScore("§fUlt: §a" + manager.cooldownText(p, "ult")).setScore(line--);
        obj.getScore("§7----------------").setScore(line--);

        p.setScoreboard(board);
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        boards.clear();
    }
}
