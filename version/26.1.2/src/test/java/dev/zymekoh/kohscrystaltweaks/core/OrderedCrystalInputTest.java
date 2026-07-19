package dev.zymekoh.kohscrystaltweaks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class OrderedCrystalInputTest {
    @AfterEach
    void clearQueue() {
        OrderedCrystalInput.clear();
    }

    @Test
    void preservesPhysicalSlotAndUseOrder() {
        OrderedCrystalInput.recordSlotChangeUnchecked(0, 2);
        OrderedCrystalInput.recordInputUnchecked(false, true, 2);
        OrderedCrystalInput.recordSlotChangeUnchecked(2, 3);
        OrderedCrystalInput.recordInputUnchecked(false, true, 3);

        OrderedCrystalInput.Batch batch = OrderedCrystalInput.drain();

        assertEquals(0, batch.initialSlot());
        assertEquals(List.of(
                new OrderedCrystalInput.Entry(OrderedCrystalInput.Action.SELECT_SLOT, 2),
                new OrderedCrystalInput.Entry(OrderedCrystalInput.Action.USE, -1),
                new OrderedCrystalInput.Entry(OrderedCrystalInput.Action.SELECT_SLOT, 3),
                new OrderedCrystalInput.Entry(OrderedCrystalInput.Action.USE, -1)
        ), batch.entries());
    }

    @Test
    void keepsVanillaAttackBeforeUseForOneSharedPhysicalBinding() {
        OrderedCrystalInput.recordInputUnchecked(true, true, 4);

        OrderedCrystalInput.Batch batch = OrderedCrystalInput.drain();

        assertEquals(List.of(
                new OrderedCrystalInput.Entry(OrderedCrystalInput.Action.ATTACK, -1),
                new OrderedCrystalInput.Entry(OrderedCrystalInput.Action.USE, -1)
        ), batch.entries());
    }

    @Test
    void replaysOnlyWhenVanillaCouldLosePhysicalOrder() {
        OrderedCrystalInput.recordInputUnchecked(false, true, 4);
        assertFalse(OrderedCrystalInput.drain().requiresOrderedReplay());

        OrderedCrystalInput.recordInputUnchecked(true, true, 4);
        assertTrue(OrderedCrystalInput.drain().requiresOrderedReplay());

        OrderedCrystalInput.recordSlotChangeUnchecked(4, 5);
        OrderedCrystalInput.recordInputUnchecked(false, true, 5);
        assertTrue(OrderedCrystalInput.drain().requiresOrderedReplay());
    }

    @Test
    void claimsOnlyOneExclusiveActionForImmediateDispatch() {
        OrderedCrystalInput.recordInputUnchecked(false, true, 4);
        assertTrue(OrderedCrystalInput.claimExclusiveActionForImmediateDispatch(
                OrderedCrystalInput.Action.USE, true));
        assertTrue(OrderedCrystalInput.drain().isEmpty());

        OrderedCrystalInput.recordInputUnchecked(true, false, 4);
        assertTrue(OrderedCrystalInput.claimExclusiveActionForImmediateDispatch(
                OrderedCrystalInput.Action.ATTACK, true));
        assertTrue(OrderedCrystalInput.drain().isEmpty());

        OrderedCrystalInput.recordSlotChangeUnchecked(4, 5);
        OrderedCrystalInput.recordInputUnchecked(false, true, 5);
        assertFalse(OrderedCrystalInput.claimExclusiveActionForImmediateDispatch(
                OrderedCrystalInput.Action.USE, true));
    }
}
