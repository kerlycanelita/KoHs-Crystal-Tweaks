package com.zymekoh.crystaltweaks;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CrystalTweaksClient implements ClientModInitializer {
    public static final String MOD_ID = "crystal_tweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Crystal Tweaks initialized");
    }
}
