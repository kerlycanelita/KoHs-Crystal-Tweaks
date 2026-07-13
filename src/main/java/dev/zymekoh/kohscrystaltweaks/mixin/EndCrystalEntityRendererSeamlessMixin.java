package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.core.SeamlessCrystalBridge;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalEntityRenderer.class)
public abstract class EndCrystalEntityRendererSeamlessMixin {
    @Unique
    private static final Map<EndCrystalEntityRenderState, Integer> KCT_STATE_TO_ID = new IdentityHashMap<>();
    @Unique
    private static final ThreadLocal<Boolean> KCT_SHIFTED = ThreadLocal.withInitial(() -> false);
    @Unique
    private static final ThreadLocal<Integer> KCT_OLD_AGE = ThreadLocal.withInitial(() -> 0);
    @Unique
    private static boolean kctAgeResolved;
    @Unique
    private static Field kctAgeField;

    @Inject(method = "updateRenderState", at = @At("HEAD"))
    private void kct$shiftAgeBeforeUpdate(EndCrystalEntity entity, EndCrystalEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!KoHsCrystalTweaksConfig.get().clientSideCrystalsEnabled || !KoHsCrystalTweaksConfig.get().seamlessEnabled) {
            return;
        }

        int id = entity.getId();
        int delta = SeamlessCrystalBridge.ageDeltaFor(id);
        if (delta == 0) {
            return;
        }

        Integer oldAge = getAge(entity);
        if (oldAge == null) {
            return;
        }

        if (!setAge(entity, oldAge + delta)) {
            return;
        }

        KCT_SHIFTED.set(true);
        KCT_OLD_AGE.set(oldAge);
    }

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void kct$trackAndRestore(EndCrystalEntity entity, EndCrystalEntityRenderState state, float tickDelta, CallbackInfo ci) {
        KCT_STATE_TO_ID.put(state, entity.getId());

        if (KCT_SHIFTED.get()) {
            setAge(entity, KCT_OLD_AGE.get());
            KCT_SHIFTED.set(false);
            KCT_OLD_AGE.set(0);
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void kct$hideRealForSeamless(
            EndCrystalEntityRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState camera,
            CallbackInfo ci) {
        if (!KoHsCrystalTweaksConfig.get().clientSideCrystalsEnabled || !KoHsCrystalTweaksConfig.get().seamlessEnabled) {
            return;
        }

        Integer id = KCT_STATE_TO_ID.get(state);
        if (id == null) {
            return;
        }

        if (SeamlessCrystalBridge.shouldHide(id, CrystalPredictor.debugTick())) {
            ci.cancel();
        }
    }

    @Unique
    private static Integer getAge(Entity entity) {
        if (entity instanceof EntityAgeAccessor accessor) {
            return accessor.kct$getAge();
        }

        Field field = resolveAgeField();
        if (field == null) {
            return null;
        }
        try {
            return field.getInt(entity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static boolean setAge(Entity entity, int age) {
        if (entity instanceof EntityAgeAccessor accessor) {
            accessor.kct$setAge(age);
            return true;
        }

        Field field = resolveAgeField();
        if (field == null) {
            return false;
        }
        try {
            field.setInt(entity, age);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Unique
    private static Field resolveAgeField() {
        if (kctAgeResolved) {
            return kctAgeField;
        }

        synchronized (EndCrystalEntityRendererSeamlessMixin.class) {
            if (kctAgeResolved) {
                return kctAgeField;
            }
            try {
                Field field = Entity.class.getDeclaredField("age");
                field.setAccessible(true);
                kctAgeField = field;
            } catch (Throwable ignored) {
                kctAgeField = null;
            }
            kctAgeResolved = true;
            return kctAgeField;
        }
    }
}
