package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Retargets a crystal click to obsidian that was just placed by vanilla client prediction.
 * It never creates, delays, repeats, or sends an additional interaction packet.
 */
public final class CrystalPlacementFix {
    private static final int MAX_PENDING_TICKS = 4;
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    private static PendingObsidian pendingObsidian;
    private static int clientTick;

    private CrystalPlacementFix() {
    }

    public static boolean isEnabled() {
        return KoHsCrystalTweaksConfig.get().placementFixEnabled;
    }

    public static void clientTick() {
        clientTick++;
        if (!isEnabled() || CLIENT.world == null || isExpired(pendingObsidian)) {
            pendingObsidian = null;
        }
    }

    public static void reset() {
        pendingObsidian = null;
        clientTick = 0;
        OrderedCrystalInput.clear();
    }

    public static void recordObsidianPlacement(ClientPlayerEntity player, BlockHitResult hit) {
        if (!isEnabled() || player == null || hit == null || CLIENT.world == null) {
            return;
        }

        BlockPos placed = findPlacedObsidian(CLIENT.world, hit);
        if (placed != null) {
            pendingObsidian = new PendingObsidian(placed.toImmutable(), clientTick);
        }
    }

    public static BlockHitResult retargetCrystal(ClientPlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!isEnabled()
                || player == null
                || hit == null
                || !player.getStackInHand(hand).isOf(Items.END_CRYSTAL)) {
            return hit;
        }

        ClientWorld world = CLIENT.world;
        PendingObsidian pending = pendingObsidian;
        if (world == null || pending == null || isExpired(pending)) {
            pendingObsidian = null;
            return hit;
        }

        BlockPos base = pending.position();
        if (!isObsidian(world, base) || !hasCrystalSpace(world, base.up())) {
            return hit;
        }
        if (isValidCrystalBase(world, hit.getBlockPos()) || !isSameFastPlacement(hit, base)) {
            return hit;
        }

        pendingObsidian = null;
        Vec3d topCenter = new Vec3d(base.getX() + 0.5D, base.getY() + 1.0D, base.getZ() + 0.5D);
        return new BlockHitResult(topCenter, Direction.UP, base, false);
    }

    private static BlockPos findPlacedObsidian(ClientWorld world, BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        BlockPos offset = clicked.offset(hit.getSide());
        if (isObsidian(world, offset)) {
            return offset;
        }
        return isObsidian(world, clicked) ? clicked : null;
    }

    private static boolean isSameFastPlacement(BlockHitResult hit, BlockPos base) {
        BlockPos clicked = hit.getBlockPos();
        if (clicked.equals(base) || clicked.offset(hit.getSide()).equals(base)) {
            return true;
        }

        return Math.abs(clicked.getX() - base.getX()) <= 1
                && Math.abs(clicked.getY() - base.getY()) <= 1
                && Math.abs(clicked.getZ() - base.getZ()) <= 1;
    }

    private static boolean isValidCrystalBase(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return (state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK))
                && hasCrystalSpace(world, pos.up());
    }

    private static boolean isObsidian(ClientWorld world, BlockPos pos) {
        return world.getBlockState(pos).isOf(Blocks.OBSIDIAN);
    }

    private static boolean hasCrystalSpace(ClientWorld world, BlockPos crystalPos) {
        if (!world.isAir(crystalPos)) {
            return false;
        }

        Box box = new Box(
                crystalPos.getX(),
                crystalPos.getY(),
                crystalPos.getZ(),
                crystalPos.getX() + 1.0D,
                crystalPos.getY() + 2.0D,
                crystalPos.getZ() + 1.0D);
        return world.getOtherEntities((Entity) null, box).isEmpty();
    }

    private static boolean isExpired(PendingObsidian pending) {
        return pending != null && clientTick - pending.createdAtTick() > MAX_PENDING_TICKS;
    }

    private record PendingObsidian(BlockPos position, int createdAtTick) {
    }
}
