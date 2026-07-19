package dev.zymekoh.kohscrystaltweaks.core;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Retargets a crystal click to obsidian that was just placed by vanilla client prediction.
 * It never creates, delays, repeats, or sends an additional interaction packet.
 */
public final class CrystalPlacementFix {
    private static final int MAX_PENDING_TICKS = 4;
    private static final Minecraft MINECRAFT = Minecraft.getInstance();

    private static PendingObsidian pendingObsidian;
    private static int clientTick;

    private CrystalPlacementFix() {
    }

    public static boolean isEnabled() {
        return KoHsCrystalTweaksConfig.get().placementFixEnabled;
    }

    public static void clientTick() {
        clientTick++;
        if (!isEnabled() || MINECRAFT.level == null || isExpired(pendingObsidian)) {
            pendingObsidian = null;
        }
    }

    public static void reset() {
        pendingObsidian = null;
        clientTick = 0;
        OrderedCrystalInput.clear();
    }

    public static void recordObsidianPlacement(LocalPlayer player, BlockHitResult hit) {
        if (!isEnabled() || player == null || hit == null || MINECRAFT.level == null) {
            return;
        }

        BlockPos placed = findPlacedObsidian(MINECRAFT.level, hit);
        if (placed != null) {
            pendingObsidian = new PendingObsidian(placed.immutable(), clientTick);
        }
    }

    public static BlockHitResult retargetCrystal(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        if (!isEnabled()
                || player == null
                || hit == null
                || !player.getItemInHand(hand).is(Items.END_CRYSTAL)) {
            return hit;
        }

        ClientLevel level = MINECRAFT.level;
        PendingObsidian pending = pendingObsidian;
        if (level == null || pending == null || isExpired(pending)) {
            pendingObsidian = null;
            return hit;
        }

        if (isCrystalBase(level, hit.getBlockPos())) {
            pendingObsidian = null;
            return hit;
        }

        BlockPos base = pending.position();
        if (!isSameFastPlacement(hit, base)) {
            return hit;
        }
        if (!isObsidian(level, base)) {
            pendingObsidian = null;
            return hit;
        }

        pendingObsidian = null;
        Vec3 topCenter = new Vec3(base.getX() + 0.5D, base.getY() + 1.0D, base.getZ() + 0.5D);
        return new BlockHitResult(topCenter, Direction.UP, base, false);
    }

    private static BlockPos findPlacedObsidian(ClientLevel level, BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        BlockPos offset = clicked.relative(hit.getDirection());
        if (isObsidian(level, offset)) {
            return offset;
        }
        return isObsidian(level, clicked) ? clicked : null;
    }

    static boolean isSameFastPlacement(BlockHitResult hit, BlockPos base) {
        BlockPos clicked = hit.getBlockPos();
        return clicked.equals(base) || clicked.relative(hit.getDirection()).equals(base);
    }

    private static boolean isCrystalBase(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    private static boolean isObsidian(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.OBSIDIAN);
    }

    private static boolean isExpired(PendingObsidian pending) {
        return pending != null && clientTick - pending.createdAtTick() > MAX_PENDING_TICKS;
    }

    private record PendingObsidian(BlockPos position, int createdAtTick) {
    }
}
