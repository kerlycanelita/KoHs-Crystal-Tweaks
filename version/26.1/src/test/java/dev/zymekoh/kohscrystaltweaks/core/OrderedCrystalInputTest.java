package dev.zymekoh.kohscrystaltweaks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
