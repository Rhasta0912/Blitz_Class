package com.stellinova.blitz.expansion;

import com.stellinova.blitz.bridge.BlitzEvoBridge;
import com.stellinova.blitz.manager.BlitzManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class BlitzExpansion extends PlaceholderExpansion {

    private final Plugin plugin;
    private final BlitzManager manager;
    private final BlitzEvoBridge evo;

    public BlitzExpansion(Plugin plugin, BlitzManager manager, BlitzEvoBridge evo) {
        this.plugin = plugin;
        this.manager = manager;
        this.evo = evo;
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getIdentifier() {
        return "blitz";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player p, String id) {
        if (p == null) return "";
        switch (id.toLowerCase()) {
            case "stage":
                return String.valueOf(evo.stage(p));
            case "cd_bolt":
                return manager.cooldownText(p, "bolt");
            case "cd_flash":
                return manager.cooldownText(p, "flash");
            case "cd_shock":
                return manager.cooldownText(p, "shock");
            case "cd_counter":
                return manager.cooldownText(p, "counter");
            case "cd_ult":
                return manager.cooldownText(p, "ult");
            default:
                return "";
        }
    }
}
