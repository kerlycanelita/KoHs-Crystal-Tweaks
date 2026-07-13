package dev.zymekoh.kohscrystaltweaks;

import dev.zymekoh.kohscrystaltweaks.compat.IncompatibilityManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KoHsCrystalTweaks implements ModInitializer {
    public static final String MOD_ID = "kohs_crystal_tweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        IncompatibilityManager.initialize();
        if (IncompatibilityManager.isBlocked()) {
            LOGGER.error("[{}] Runtime initialization disabled because an incompatible mod was detected", MOD_ID);
            return;
        }
        LOGGER.info("[{}] Init", MOD_ID);
    }
}
