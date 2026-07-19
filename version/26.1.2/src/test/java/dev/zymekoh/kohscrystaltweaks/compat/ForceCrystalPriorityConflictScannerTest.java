package dev.zymekoh.kohscrystaltweaks.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.zymekoh.kohscrystaltweaks.compat.ForceCrystalPriorityConflictScanner.ConflictPoint;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ForceCrystalPriorityConflictScannerTest {
    @Test
    void reportsOnlyTheExactHotbarKeyMappingHook() {
        MixinConflictScanner.MixinSignature feature = signature(
                "net.minecraft.client.KeyMapping",
                "click",
                "dev.zymekoh.kohscrystaltweaks.mixin.KeyMappingForceCrystalPvpPriorityMixin");
        MixinConflictScanner.MixinSignature overlap = signature(
                "net.minecraft.client.KeyMapping", "click", "example.fastplace.FastHotbarKeyMixin");
        MixinConflictScanner.MixinSignature unrelated = signature(
                "net.minecraft.client.KeyMapping", "set", "example.fastplace.FastHotbarKeyMixin");

        Set<ConflictPoint> exact = ForceCrystalPriorityConflictScanner.findPriorityOverlaps(
                List.of(feature), List.of(overlap));
        Set<ConflictPoint> different = ForceCrystalPriorityConflictScanner.findPriorityOverlaps(
                List.of(feature), List.of(unrelated));

        assertEquals(Set.of(new ConflictPoint(
                "net.minecraft.client.KeyMapping",
                "click",
                "example.fastplace.FastHotbarKeyMixin")), exact);
        assertTrue(different.isEmpty());
    }

    @Test
    void reportsDirectMutationOfTheFeatureClass() {
        MixinConflictScanner.MixinSignature feature = signature(
                "net.minecraft.client.KeyMapping",
                "click",
                "dev.zymekoh.kohscrystaltweaks.mixin.KeyMappingForceCrystalPvpPriorityMixin");
        MixinConflictScanner.MixinSignature direct = new MixinConflictScanner.MixinSignature(
                Set.of("dev.zymekoh.kohscrystaltweaks.core.ForceCrystalPvpPriority"),
                Set.of("selectExplicitHotbarSlot"),
                "example.integration.KoHsPriorityMixin");

        assertEquals(Set.of(new ConflictPoint(
                        "dev.zymekoh.kohscrystaltweaks.core.ForceCrystalPvpPriority",
                        "selectExplicitHotbarSlot",
                        "example.integration.KoHsPriorityMixin")),
                ForceCrystalPriorityConflictScanner.findPriorityOverlaps(List.of(feature), List.of(direct)));
    }

    @Test
    void ignoresAnUnrelatedMixinOnTheSameKeyMappingCallback() {
        MixinConflictScanner.MixinSignature feature = signature(
                "net.minecraft.client.KeyMapping",
                "click",
                "dev.zymekoh.kohscrystaltweaks.mixin.KeyMappingForceCrystalPvpPriorityMixin");
        MixinConflictScanner.MixinSignature accessibility = signature(
                "net.minecraft.client.KeyMapping",
                "click",
                "example.accessibility.KeyNarrationMixin");

        assertTrue(ForceCrystalPriorityConflictScanner.findPriorityOverlaps(
                List.of(feature), List.of(accessibility)).isEmpty());
    }

    private static MixinConflictScanner.MixinSignature signature(
            String targetClass,
            String method,
            String mixinClass
    ) {
        return new MixinConflictScanner.MixinSignature(
                Set.of(targetClass), Set.of(method), mixinClass);
    }
}
