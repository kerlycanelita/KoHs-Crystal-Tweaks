package dev.zymekoh.kohscrystaltweaks.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

final class KoHsCrystalTweaksConfigTest {
    @Test
    void safeCrystalDefaultsToEnabledForExistingConfigFilesWithoutTheNewField() {
        KoHsCrystalTweaksConfig config = new Gson().fromJson("{}", KoHsCrystalTweaksConfig.class);

        assertTrue(config.safeCrystalEnabled);
    }
}
