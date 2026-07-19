package dev.zymekoh.kohscrystaltweaks.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ForceCrystalPvpPriorityTest {
    @Test
    void onlyEndCrystalAndObsidianReceiveTheFastSelectionPath() {
        assertTrue(ForceCrystalPvpPriority.isPriorityItem(true, false));
        assertTrue(ForceCrystalPvpPriority.isPriorityItem(false, true));
        assertTrue(ForceCrystalPvpPriority.isPriorityItem(true, true));
    }

    @Test
    void everyOtherItemUsesVanillaSelectionTiming() {
        assertFalse(ForceCrystalPvpPriority.isPriorityItem(false, false));
    }
}
