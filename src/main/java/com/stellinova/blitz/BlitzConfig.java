package com.stellinova.blitz.core;

import org.bukkit.plugin.Plugin;

public class BlitzConfig {

    private final Plugin plugin;

    public BlitzConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public long cooldown(String key) {
        return plugin.getConfig().getLong("cooldowns." + key, 3000L);
    }

    public double scalar(String key, double def) {
        return plugin.getConfig().getDouble("scalars." + key, def);
    }

    public void reload() {
        plugin.reloadConfig();
    }
}
