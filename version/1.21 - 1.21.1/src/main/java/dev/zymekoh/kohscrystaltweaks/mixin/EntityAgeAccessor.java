package dev.zymekoh.kohscrystaltweaks.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAgeAccessor {
    @Accessor("age")
    int kct$getAge();

    @Accessor("age")
    void kct$setAge(int age);
}
