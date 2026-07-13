package dev.zymekoh.kohscrystaltweaks.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

final class KoHsCrystalTweaksConfigTest {
    @Test
    void optionalPredictionAndSafetyFeaturesDefaultToDisabledWhenFieldsAreMissing() {
        KoHsCrystalTweaksConfig config = new Gson().fromJson("{}", KoHsCrystalTweaksConfig.class);

        assertFalse(config.safeCrystalEnabled);
        assertFalse(config.clientSideCrystalsEnabled);
        assertFalse(config.seamlessEnabled);
    }
}
