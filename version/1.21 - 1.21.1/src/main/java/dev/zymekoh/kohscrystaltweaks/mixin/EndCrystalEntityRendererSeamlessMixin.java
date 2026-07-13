package dev.zymekoh.kohscrystaltweaks.mixin;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import dev.zymekoh.kohscrystaltweaks.core.SeamlessCrystalBridge;
import java.lang.reflect.Field;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalEntityRenderer.class)
public abstract class EndCrystalEntityRendererSeamlessMixin {
    @Shadow
    @Final
    private ModelPart core;

    @Shadow
    @Final
    private ModelPart frame;

    @Unique
    private static final ThreadLocal<Boolean> KCT_SHIFTED = ThreadLocal.withInitial(() -> false);
    @Unique
    private static final ThreadLocal<Integer> KCT_OLD_AGE = ThreadLocal.withInitial(() -> 0);
    @Unique
    private static boolean kctAgeResolved;
    @Unique
    private static Field kctAgeField;

    @Inject(
            method = "render(Lnet/minecraft/entity/decoration/EndCrystalEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void kct$beforeRender(
            EndCrystalEntity entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        if (!KoHsCrystalTweaksConfig.get().clientSideCrystalsEnabled || !KoHsCrystalTweaksConfig.get().seamlessEnabled) {
            return;
        }

        int id = entity.getId();
        if (SeamlessCrystalBridge.shouldHide(id, CrystalPredictor.debugTick())) {
            ci.cancel();
            return;
        }

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

    @Inject(
            method = "render(Lnet/minecraft/entity/decoration/EndCrystalEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL"))
    private void kct$restoreAgeAfterRender(
            EndCrystalEntity entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci) {
        if (KCT_SHIFTED.get()) {
            setAge(entity, KCT_OLD_AGE.get());
            KCT_SHIFTED.set(false);
            KCT_OLD_AGE.set(0);
        }
    }

    @Redirect(
            method = "render(Lnet/minecraft/entity/decoration/EndCrystalEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/ModelPart;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"))
    private void kct$renderCrystalPartWithTint(
            ModelPart part, MatrixStack matrices, VertexConsumer vertices, int light, int overlay) {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        if (!config.crystalTintEnabled) {
            part.render(matrices, vertices, light, overlay);
            return;
        }

        int color = 0xFFFFFFFF;
        if (part == this.frame) {
            color = KoHsCrystalTweaksConfig.getCrystalFrameTintArgb();
        } else if (part == this.core) {
            color = KoHsCrystalTweaksConfig.getCrystalCoreTintArgb();
        }

        part.render(matrices, vertices, light, overlay, color);
    }

    @Redirect(
            method = "render(Lnet/minecraft/entity/decoration/EndCrystalEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/EndCrystalEntityRenderer;getYOffset(Lnet/minecraft/entity/decoration/EndCrystalEntity;F)F"))
    private float kct$applyCrystalFloatTweaks(EndCrystalEntity entity, float tickDelta) {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        if (config.staticCrystalEnabled || !config.crystalFlotationEnabled) {
            return kct$getYOffsetForAge(0.0f);
        }
        return EndCrystalEntityRenderer.getYOffset(entity, tickDelta);
    }

    @Redirect(
            method = "render(Lnet/minecraft/entity/decoration/EndCrystalEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/RotationAxis;rotationDegrees(F)Lorg/joml/Quaternionf;",
                    ordinal = 0))
    private Quaternionf kct$applyCrystalSpinFirst(RotationAxis axis, float degrees) {
        return kct$rotationDegrees(axis, degrees);
    }

    @Redirect(
            method = "render(Lnet/minecraft/entity/decoration/EndCrystalEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/RotationAxis;rotationDegrees(F)Lorg/joml/Quaternionf;",
                    ordinal = 1))
    private Quaternionf kct$applyCrystalSpinSecond(RotationAxis axis, float degrees) {
        return kct$rotationDegrees(axis, degrees);
    }

    @Redirect(
            method = "render(Lnet/minecraft/entity/decoration/EndCrystalEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/RotationAxis;rotationDegrees(F)Lorg/joml/Quaternionf;",
                    ordinal = 2))
    private Quaternionf kct$applyCrystalSpinThird(RotationAxis axis, float degrees) {
        return kct$rotationDegrees(axis, degrees);
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

    @Unique
    private static Quaternionf kct$rotationDegrees(RotationAxis axis, float degrees) {
        KoHsCrystalTweaksConfig config = KoHsCrystalTweaksConfig.get();
        float adjustedDegrees = config.staticCrystalEnabled ? 0.0f : degrees * config.crystalSpinSpeed;
        return axis.rotationDegrees(adjustedDegrees);
    }

    @Unique
    private static float kct$getYOffsetForAge(float age) {
        float offset = MathHelper.sin(age * 0.2f) / 2.0f + 0.5f;
        offset = (offset * offset + offset) * 0.4f;
        return offset - 1.4f;
    }
}
