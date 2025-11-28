package com.stellinova.blitz.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Access bridge:
 * - Permissions (blitz.use / blitz.admin)
 * - Optional RuneSelector integration via reflection (RuneType.BLITZ)
 */
public final class BlitzAccessBridge {

    private static Plugin plugin;

    // RuneSelector reflection cache
    private static boolean runeChecked = false;
    private static Object runeService;
    private static Method getActiveRuneMethod;
    private static Class<?> runeTypeClass;
    private static Object blitzRuneConstant;

    private BlitzAccessBridge() {}

    public static void init(Plugin pl) {
        plugin = pl;
    }

    public static boolean hasUsePerm(Player p) {
        return p.hasPermission("blitz.use");
    }

    public static boolean isAdmin(Player p) {
        return p.hasPermission("blitz.admin");
    }

    public static boolean hasBlitzRune(Player p) {
        tryInitRuneSelector();
        if (runeService == null || getActiveRuneMethod == null || blitzRuneConstant == null) {
            // RuneSelector not present or BLITZ not yet defined: do not hard lock.
            return true;
        }
        try {
            Object current = getActiveRuneMethod.invoke(runeService, p);
            return current == blitzRuneConstant || (current != null && current.equals(blitzRuneConstant));
        } catch (Throwable t) {
            log("RuneSelector reflection failed: " + t.getMessage());
            return true;
        }
    }

    public static void grant(Player p) {
        // Give Blitz rune via RuneSelector if available
        tryInitRuneSelector();
        if (runeService != null && blitzRuneConstant != null) {
            try {
                Method setMethod = runeService.getClass().getMethod("setActiveRune", Player.class, runeTypeClass);
                setMethod.invoke(runeService, p, blitzRuneConstant);
            } catch (Throwable t) {
                log("Failed to grant Blitz rune: " + t.getMessage());
            }
        }
    }

    public static void revoke(Player p) {
        // Remove Blitz rune via RuneSelector if available
        tryInitRuneSelector();
        if (runeService != null && runeTypeClass != null) {
            try {
                Object noneRune = null;
                Object[] constants = runeTypeClass.getEnumConstants();
                if (constants != null) {
                    for (Object c : constants) {
                        if (c.toString().equalsIgnoreCase("NONE")) {
                            noneRune = c;
                            break;
                        }
                    }
                }
                if (noneRune != null) {
                    Method setMethod = runeService.getClass().getMethod("setActiveRune", Player.class, runeTypeClass);
                    setMethod.invoke(runeService, p, noneRune);
                }
            } catch (Throwable t) {
                log("Failed to revoke Blitz rune: " + t.getMessage());
            }
        }
    }

    private static void tryInitRuneSelector() {
        if (runeChecked) return;
        runeChecked = true;

        try {
            ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
            Class<?> helperClass = Class.forName("com.stellinova.runeselector.api.RuneServiceHelper", false, cl);
            Method getMethod = helperClass.getMethod("get");
            Object svc = getMethod.invoke(null);
            if (svc == null) {
                log("RuneSelector service helper returned null.");
                return;
            }
            runeService = svc;
            Class<?> serviceClass = svc.getClass();
            getActiveRuneMethod = serviceClass.getMethod("getActiveRune", Player.class);

            runeTypeClass = Class.forName("com.stellinova.runeselector.api.RuneType", false, cl);
            Object[] constants = runeTypeClass.getEnumConstants();
            if (constants != null) {
                for (Object c : constants) {
                    if (c.toString().equalsIgnoreCase("BLITZ")) {
                        blitzRuneConstant = c;
                        break;
                    }
                }
            }

            if (blitzRuneConstant == null) {
                log("RuneType.BLITZ not found in RuneSelector API.");
            }
        } catch (Throwable t) {
            log("RuneSelector reflection init failed: " + t.getMessage());
            runeService = null;
            getActiveRuneMethod = null;
            runeTypeClass = null;
            blitzRuneConstant = null;
        }
    }

    private static void log(String msg) {
        if (plugin != null) {
            Logger l = plugin.getLogger();
            if (l != null) l.warning(msg);
        }
    }
}
