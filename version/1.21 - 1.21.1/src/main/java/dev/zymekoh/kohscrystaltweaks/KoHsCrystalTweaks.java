package dev.zymekoh.kohscrystaltweaks;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KoHsCrystalTweaks implements ModInitializer {
    public static final String MOD_ID = "kohs_crystal_tweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] Init", MOD_ID);
    }
}
