package dev.zymekoh.kohscrystaltweaks.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.zymekoh.kohscrystaltweaks.compat.IncompatibilityManager.ConflictPoint;
import dev.zymekoh.kohscrystaltweaks.mixin.MinecraftClientPassThroughLocalCrystalMixin;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

final class MixinConflictScannerTest {
    @Test
    void readsTheRealKoHsCriticalMinecraftClientHooks() throws IOException {
        byte[] bytes = classBytes(MinecraftClientPassThroughLocalCrystalMixin.class);

        List<MixinConflictScanner.MixinSignature> signatures = MixinConflictScanner.inspectMixinClass(
                MinecraftClientPassThroughLocalCrystalMixin.class.getName(), bytes);

        assertEquals(1, signatures.size());
        assertEquals(Set.of("handleInputEvents", "doAttack", "doItemUse"),
                signatures.getFirst().targetMethods());
    }

    @Test
    void extractsTargetsAndNormalizedInjectionMethodsFromBytecode() throws IOException {
        byte[] bytes = classBytes(FixtureCrystalMixin.class);

        List<MixinConflictScanner.MixinSignature> signatures =
                MixinConflictScanner.inspectMixinClass(FixtureCrystalMixin.class.getName(), bytes);

        assertEquals(1, signatures.size());
        assertEquals(Set.of("net.minecraft.client.MinecraftClient"), signatures.getFirst().targetClasses());
        assertEquals(Set.of("doAttack", "doItemUse"), signatures.getFirst().targetMethods());
    }

    @Test
    void extractsModernDescBasedInjectionTargets() throws IOException {
        List<MixinConflictScanner.MixinSignature> signatures = MixinConflictScanner.inspectMixinClass(
                FixtureDescMixin.class.getName(), classBytes(FixtureDescMixin.class));

        assertEquals(Set.of("doAttack"), signatures.getFirst().targetMethods());
    }

    @Test
    void blocksOnlyAnExactCriticalMethodOverlapForCrystalRelatedMods() {
        MixinConflictScanner.MixinSignature own = signature("doAttack", "KoHsAttackMixin");
        MixinConflictScanner.MixinSignature sameMethod = signature("doAttack", "example.crystal.FastCrystalMixin");
        MixinConflictScanner.MixinSignature differentMethod = signature("tick", "example.crystal.FastCrystalMixin");

        Set<ConflictPoint> exact = MixinConflictScanner.findCriticalOverlaps(
                List.of(own), List.of(sameMethod), false);
        Set<ConflictPoint> different = MixinConflictScanner.findCriticalOverlaps(
                List.of(own), List.of(differentMethod), false);

        assertEquals(Set.of(new ConflictPoint("MinecraftClient", "doAttack", "example.crystal.FastCrystalMixin")), exact);
        assertTrue(different.isEmpty());
    }

    @Test
    void doesNotBlockAnUnrelatedModEvenWhenClassAndMethodMatch() {
        MixinConflictScanner.MixinSignature own = signature("doAttack", "KoHsAttackMixin");
        MixinConflictScanner.MixinSignature unrelated = signature("doAttack", "example.accessibility.InputMixin");

        assertFalse(MixinConflictScanner.findCriticalOverlaps(
                List.of(own), List.of(unrelated), false).iterator().hasNext());
    }

    private static MixinConflictScanner.MixinSignature signature(String method, String mixinClass) {
        return new MixinConflictScanner.MixinSignature(
                Set.of("net.minecraft.client.MinecraftClient"), Set.of(method), mixinClass);
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing class resource: " + resource);
            }
            return stream.readAllBytes();
        }
    }

    @Mixin(targets = "net.minecraft.client.MinecraftClient")
    private abstract static class FixtureCrystalMixin {
        @Inject(method = {"doAttack()Z", "doItemUse()V"}, at = @At("HEAD"))
        private void fixture(CallbackInfo callbackInfo) {
        }
    }

    @Mixin(targets = "net.minecraft.client.MinecraftClient")
    private abstract static class FixtureDescMixin {
        @Inject(target = @Desc(value = "doAttack", owner = Object.class), at = @At("HEAD"))
        private void fixture(CallbackInfo callbackInfo) {
        }
    }
}
