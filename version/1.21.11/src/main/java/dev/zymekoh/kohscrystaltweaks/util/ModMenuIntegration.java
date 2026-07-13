package dev.zymekoh.kohscrystaltweaks.util;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.zymekoh.kohscrystaltweaks.compat.IncompatibilityManager;
import dev.zymekoh.kohscrystaltweaks.gui.IncompatibilityScreen;
import dev.zymekoh.kohscrystaltweaks.gui.KoHsCrystalTweaksConfigScreen;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> IncompatibilityManager.isBlocked()
                ? new IncompatibilityScreen()
                : new KoHsCrystalTweaksConfigScreen(parent);
    }
}
