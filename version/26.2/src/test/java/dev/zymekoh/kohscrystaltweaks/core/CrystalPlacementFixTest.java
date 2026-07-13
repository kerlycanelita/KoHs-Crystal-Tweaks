package dev.zymekoh.kohscrystaltweaks.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class CrystalPlacementFixTest {
    @Test
    void acceptsOnlyTheRecordedBaseOrTheExactPlacementOffset() {
        BlockPos base = new BlockPos(10, 64, 10);

        assertTrue(CrystalPlacementFix.isSameFastPlacement(hit(base, Direction.UP), base));
        assertTrue(CrystalPlacementFix.isSameFastPlacement(hit(base.below(), Direction.UP), base));
        assertFalse(CrystalPlacementFix.isSameFastPlacement(
                hit(base.offset(1, 0, 1), Direction.UP), base));
    }

    private static BlockHitResult hit(BlockPos position, Direction side) {
        return new BlockHitResult(Vec3.atCenterOf(position), side, position, false);
    }
}
