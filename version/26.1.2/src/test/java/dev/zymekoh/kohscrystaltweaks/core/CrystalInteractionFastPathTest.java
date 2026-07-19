package dev.zymekoh.kohscrystaltweaks.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CrystalInteractionFastPathTest {
    @Test
    void keepsAPlayerRequestedAttackThroughTheConfiguredLatencyWindow() {
        assertFalse(CrystalInteractionFastPath.isExpired(10, 50));
        assertTrue(CrystalInteractionFastPath.isExpired(10, 51));
    }

    @Test
    void suppressesOnlyASecondAttackInTheSameClientTick() {
        assertTrue(CrystalInteractionFastPath.isDuplicateAttack(25, 25));
        assertFalse(CrystalInteractionFastPath.isDuplicateAttack(24, 25));
    }
}
