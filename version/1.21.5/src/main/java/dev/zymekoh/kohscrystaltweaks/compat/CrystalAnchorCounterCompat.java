package dev.zymekoh.kohscrystaltweaks.compat;

import java.lang.reflect.Method;
import java.util.UUID;

public final class CrystalAnchorCounterCompat {
    private static volatile boolean resolved;
    private static volatile Method recordCrystalBreak;

    private CrystalAnchorCounterCompat() {
    }

    public static void recordCrystalBreak(int entityId, UUID uuid) {
        if (!ensure()) {
            return;
        }
        try {
            recordCrystalBreak.invoke(null, entityId, uuid);
        } catch (Throwable ignored) {
        }
    }

    private static boolean ensure() {
        if (!resolved) {
            synchronized (CrystalAnchorCounterCompat.class) {
                if (!resolved) {
                    try {
                        Class<?> clazz = Class.forName("me.cutebow.crystalanchorcounter.client.CrystalAnchorCounterClient");
                        recordCrystalBreak = clazz.getMethod("externalRecordCrystalBreak", int.class, UUID.class);
                    } catch (Throwable ignored) {
                        recordCrystalBreak = null;
                    }
                    resolved = true;
                }
            }
        }
        return recordCrystalBreak != null;
    }
}
