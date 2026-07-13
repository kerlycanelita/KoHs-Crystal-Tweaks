package dev.zymekoh.kohscrystaltweaks.util;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.zymekoh.kohscrystaltweaks.gui.KoHsCrystalTweaksConfigScreen;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return KoHsCrystalTweaksConfigScreen::new;
    }
}
