package com.zymekoh.crystaltweaks.mixin.client;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The clicks a key has stored for the next tick: placement at the server's pace hands one back. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount")
    int crystalTweaks$clickCount();

    @Accessor("clickCount")
    void crystalTweaks$setClickCount(int clickCount);
}
