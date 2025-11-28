package com.stellinova.blitz.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * PlayerData stores cooldown timestamps and state windows.
 */
public class PlayerData {

    private static final Map<UUID, PlayerData> DATA = new HashMap<>();

    private final UUID uuid;

    // cooldown ready times (epoch ms)
    private long boltReadyAt;
    private long flashReadyAt;
    private long shockReadyAt;
    private long counterReadyAt;
    private long ultReadyAt;

    // state windows
    private long stunnedUntil;
    private long counterUntil;
    private long blitzRushUntil;

    // HUD display control
    private boolean hudEnabled = true;

    private PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public static PlayerData get(Player p) {
        return DATA.computeIfAbsent(p.getUniqueId(), PlayerData::new);
    }

    public static void remove(Player p) {
        DATA.remove(p.getUniqueId());
    }

    public void reset() {
        boltReadyAt = flashReadyAt = shockReadyAt = counterReadyAt = ultReadyAt = 0L;
        stunnedUntil = counterUntil = blitzRushUntil = 0L;
        hudEnabled = false; // Hide HUD on reset
    }

    public UUID getUuid() {
        return uuid;
    }

    public long getBoltReadyAt() { return boltReadyAt; }
    public void setBoltReadyAt(long t) { boltReadyAt = t; }

    public long getFlashReadyAt() { return flashReadyAt; }
    public void setFlashReadyAt(long t) { flashReadyAt = t; }

    public long getShockReadyAt() { return shockReadyAt; }
    public void setShockReadyAt(long t) { shockReadyAt = t; }

    public long getCounterReadyAt() { return counterReadyAt; }
    public void setCounterReadyAt(long t) { counterReadyAt = t; }

    public long getUltReadyAt() { return ultReadyAt; }
    public void setUltReadyAt(long t) { ultReadyAt = t; }

    public long getStunnedUntil() { return stunnedUntil; }
    public void setStunnedUntil(long t) { stunnedUntil = t; }

    public long getCounterUntil() { return counterUntil; }
    public void setCounterUntil(long t) { counterUntil = t; }

    public long getBlitzRushUntil() { return blitzRushUntil; }
    public void setBlitzRushUntil(long t) { blitzRushUntil = t; }

    public boolean isHudEnabled() { return hudEnabled; }
    public void setHudEnabled(boolean enabled) { hudEnabled = enabled; }
}
