package com.stellinova.blitz.core;

import com.stellinova.blitz.bridge.BlitzAccessBridge;
import com.stellinova.blitz.bridge.BlitzEvoBridge;
import com.stellinova.blitz.command.BlitzCommand;
import com.stellinova.blitz.expansion.BlitzExpansion;
import com.stellinova.blitz.hud.ScoreboardHud;
import com.stellinova.blitz.manager.BlitzManager;
import com.stellinova.blitz.player.PlayerData;
import com.stellinova.blitz.listener.BlitzListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class BlitzPlugin extends JavaPlugin {

    private static BlitzPlugin instance;

    private BlitzConfig config;
    private BlitzEvoBridge evo;
    private BlitzManager manager;
    private ScoreboardHud hud;

    public static BlitzPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        config = new BlitzConfig(this);
        evo = new BlitzEvoBridge(config);
        manager = new BlitzManager(this, evo, config);

        BlitzAccessBridge.init(this);

        hud = new ScoreboardHud(this, manager, evo);

        if (getCommand("blitz") != null) {
            getCommand("blitz").setExecutor(new BlitzCommand(this, manager, hud, evo));
        }

        Bukkit.getPluginManager().registerEvents(new BlitzListener(manager), this);

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                new BlitzExpansion(this, manager, evo).register();
            }
        } catch (Throwable ignored) {}

        for (Player p : Bukkit.getOnlinePlayers()) {
            try { manager.warm(p); } catch (Throwable ignored) {}
            try { hud.refresh(p); } catch (Throwable ignored) {}
        }

        getLogger().info("Blitz enabled.");
    }

    @Override
    public void onDisable() {
        try { if (manager != null) manager.shutdown(); } catch (Throwable ignored) {}
        try { if (hud != null) hud.shutdown(); } catch (Throwable ignored) {}

        getLogger().info("Blitz disabled.");
        instance = null;
    }

    public BlitzConfig getBlitzConfig() {
        return config;
    }

    public BlitzEvoBridge getEvoBridge() {
        return evo;
    }

    public BlitzManager getManager() {
        return manager;
    }

    public ScoreboardHud getHud() {
        return hud;
    }
}
