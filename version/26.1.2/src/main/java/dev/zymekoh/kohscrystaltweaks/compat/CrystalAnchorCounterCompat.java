package dev.zymekoh.kohscrystaltweaks.compat;

import java.lang.reflect.Method;
import java.util.UUID;

public final class CrystalAnchorCounterCompat {
    private static volatile boolean resolved;
    private static volatile Method recordCrystalBreak;

    private CrystalAnchorCounterCompat() {
    }

    public static void recordCrystalBreak(int entityId, UUID uuid) {
        if (!ensureResolved()) {
            return;
        }

        try {
            recordCrystalBreak.invoke(null, entityId, uuid);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static boolean ensureResolved() {
        if (!resolved) {
            synchronized (CrystalAnchorCounterCompat.class) {
                if (!resolved) {
                    try {
                        Class<?> clientClass = Class.forName(
                                "me.cutebow.crystalanchorcounter.client.CrystalAnchorCounterClient");
                        recordCrystalBreak = clientClass.getMethod(
                                "externalRecordCrystalBreak", int.class, UUID.class);
                    } catch (ReflectiveOperationException | LinkageError ignored) {
                        recordCrystalBreak = null;
                    }
                    resolved = true;
                }
            }
        }
        return recordCrystalBreak != null;
    }
}
