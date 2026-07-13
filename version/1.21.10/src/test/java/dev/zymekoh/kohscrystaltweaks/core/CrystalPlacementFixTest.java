package dev.zymekoh.kohscrystaltweaks.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

final class CrystalPlacementFixTest {
    @Test
    void acceptsOnlyTheRecordedBaseOrTheExactPlacementOffset() {
        BlockPos base = new BlockPos(10, 64, 10);

        assertTrue(CrystalPlacementFix.isSameFastPlacement(hit(base, Direction.UP), base));
        assertTrue(CrystalPlacementFix.isSameFastPlacement(hit(base.down(), Direction.UP), base));
        assertFalse(CrystalPlacementFix.isSameFastPlacement(
                hit(base.add(1, 0, 1), Direction.UP), base));
    }

    private static BlockHitResult hit(BlockPos position, Direction side) {
        return new BlockHitResult(Vec3d.ofCenter(position), side, position, false);
    }
}
