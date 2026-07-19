package dev.zymekoh.kohscrystaltweaks.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CrystalInputFastPathTest {
    @Test
    void acceptsOnlyOneExclusivePhysicalAction() {
        assertTrue(CrystalInputFastPath.isExclusiveAction(false, true, false));
        assertTrue(CrystalInputFastPath.isExclusiveAction(true, false, false));
        assertFalse(CrystalInputFastPath.isExclusiveAction(true, true, false));
        assertFalse(CrystalInputFastPath.isExclusiveAction(false, true, true));
        assertFalse(CrystalInputFastPath.isExclusiveAction(false, false, false));
    }
}
