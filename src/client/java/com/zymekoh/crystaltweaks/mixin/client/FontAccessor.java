package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a font finds its letters: the settings' own font asks the game's for any it does not have. */
@Mixin(Font.class)
public interface FontAccessor {
    @Accessor("provider")
    Font.Provider crystalTweaks$provider();
}
