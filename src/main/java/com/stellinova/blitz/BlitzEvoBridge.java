package com.stellinova.blitz.bridge;

import com.stellinova.blitz.core.BlitzConfig;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Evo bridge using PlaceholderAPI:
 * - %evo_stage%
 * - %evo_scalar_<key>%
 */
public class BlitzEvoBridge {

    private final BlitzConfig config;

    public BlitzEvoBridge(BlitzConfig config) {
        this.config = config;
    }

    public int stage(Player p) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                String s = PlaceholderAPI.setPlaceholders(p, "%evo_stage%");
                return clamp(Integer.parseInt(s.trim()));
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    public double scalar(Player p, String key, double def) {
        key = key.toLowerCase(Locale.ROOT);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                String ph = "%evo_scalar_" + key + "%";
                String s = PlaceholderAPI.setPlaceholders(p, ph);
                return Double.parseDouble(s.trim());
            } catch (Throwable ignored) {}
        }
        return config.scalar(key, def);
    }

    private int clamp(int s) {
        if (s < 0) s = 0;
        if (s > 3) s = 3;
        return s;
    }
}
