package com.zymekoh.crystaltweaks.client.compat;

import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads and cycles Herzium's hotbar order when Herzium is installed.
 *
 * <p>Herzium decides which hotbar slot wins when two hotbar keys land in the same tick: the last one
 * pressed, the highest (Vanilla) or the lowest. Crystal PvP presses the obsidian and crystal keys in
 * quick succession, so that choice matters there, and this lets the player see and change it from
 * the crystal settings. Nothing else is shared: Herzium owns the order and its own config file.</p>
 *
 * <p>Reflection, not a compile-time dependency: Crystal Tweaks must load without Herzium, and a
 * Herzium that renamed its config class simply turns this row off instead of crashing the screen.
 * The calls go through Herzium's own public methods, so its save, reset and preview handling run
 * exactly as when its own button is pressed.</p>
 *
 * <p>There is deliberately no bridge for the crystal optimizer. Herzium changes which slot is
 * selected and draws a switch a frame sooner; Crystal Tweaks reads the real selected slot at the
 * moment each packet leaves, after Vanilla has already applied Herzium's choice in the same tick.
 * The two never disagree about which item is in hand, so there is nothing to adapt.</p>
 */
public final class HerziumBridge {
    public static final String MOD_ID = "herzium";
    private static final String CONFIG_CLASS = "dev.zymekoh.herzium.config.HerziumConfig";

    private static volatile boolean broken;
    private static Method getMethod;
    private static Method orderMethod;
    private static Method cycleMethod;

    private HerziumBridge() {
    }

    public static boolean installed() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    /** Herzium's version as its mod metadata states it, or an empty string. */
    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    /** True while the order can be read and changed; false without Herzium or after a failed call. */
    public static boolean orderAvailable() {
        return installed() && !broken && resolve();
    }

    /** The order's enum name ({@code VANILLA}, {@code HERZIUM}, {@code VANILLA_REVERSED}), or empty. */
    public static String hotbarOrder() {
        if (!orderAvailable()) {
            return "";
        }
        try {
            Object config = getMethod.invoke(null);
            Object order = orderMethod.invoke(config);
            return order instanceof Enum<?> value ? value.name() : String.valueOf(order);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            fail(failure);
            return "";
        }
    }

    /** Moves Herzium to its next order, through Herzium's own method so it saves and resets itself. */
    public static void cycleHotbarOrder() {
        if (!orderAvailable()) {
            return;
        }
        try {
            cycleMethod.invoke(getMethod.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            fail(failure);
        }
    }

    private static synchronized boolean resolve() {
        if (getMethod != null) {
            return true;
        }
        try {
            Class<?> config = Class.forName(CONFIG_CLASS);
            Method get = config.getMethod("get");
            Method order = config.getMethod("hotbarOrder");
            Method cycle = config.getMethod("cycleHotbarOrder");
            orderMethod = order;
            cycleMethod = cycle;
            getMethod = get;
            return true;
        } catch (ReflectiveOperationException | LinkageError failure) {
            fail(failure);
            return false;
        }
    }

    private static void fail(Throwable failure) {
        if (!broken) {
            broken = true;
            CrystalTweaksClient.LOGGER.warn("Herzium is installed but its hotbar order could not be reached; "
                    + "the Herzium row in Crystal Tweaks is disabled", failure);
        }
    }
}
