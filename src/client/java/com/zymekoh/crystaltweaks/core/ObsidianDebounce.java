package com.zymekoh.crystaltweaks.core;

import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Obsidian debounce: refuses a second obsidian placement that follows the last one too closely,
 * which is how a double click or a held button stacks two blocks where the player wanted one.
 *
 * <p>It hooks the placement itself, not a key, so it covers whatever is bound to "Use": the right
 * mouse button, a side button or a remapped key alike. A refused click sends nothing, exactly as if
 * it had never been pressed; the server only ever sees the placements that do happen. That is an
 * input filter, the same kind as Safe Crystal, which is why it is off until the player turns it on.</p>
 *
 * <p>Switching to another hotbar slot ends the window. Obsidian, crystal, obsidian is a deliberate
 * sequence and is never slowed down; only obsidian followed straight by obsidian is.</p>
 */
public final class ObsidianDebounce {
    private static final int OFFHAND = -2;

    private static boolean initialized;
    private static boolean hasLast;
    private static long lastPlacedAt;
    private static int lastSlot = -1;
    private static boolean switchedSinceLast;
    private static BlockPos pendingTarget;
    private static long pendingAt;
    private static int pendingSlot;
    private static long refusedCount;

    private ObsidianDebounce() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        UseBlockCallback.EVENT.register(ObsidianDebounce::onUseBlock);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            settlePending(client.level);
            noteSlot(client.player);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> reset());
    }

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() || !(player instanceof LocalPlayer local) || !player.getItemInHand(hand).is(Items.OBSIDIAN)) {
            return InteractionResult.PASS;
        }
        settlePending(level);
        noteSlot(local);
        long now = System.nanoTime();
        int slot = slotOf(local, hand);
        if (refuses(now, slot)) {
            refusedCount++;
            return InteractionResult.FAIL;
        }
        BlockPos target = placementTarget(local, level, hit);
        if (target != null) {
            pendingTarget = target.immutable();
            pendingAt = now;
            pendingSlot = slot;
        }
        return InteractionResult.PASS;
    }

    /** True when this click would place obsidian inside the window of the last one. */
    private static boolean refuses(long now, int slot) {
        if (!CrystalVisualConfig.obsidianDebounce() || !hasLast || switchedSinceLast || slot != lastSlot) {
            return false;
        }
        long window = CrystalVisualConfig.obsidianDebounceMillis() * 1_000_000L;
        return window > 0L && now - lastPlacedAt < window;
    }

    /**
     * Where this click would put its obsidian, or {@code null} when it would not place any: a
     * container the click opens instead. Replaceable blocks such as grass are placed into.
     */
    private static BlockPos placementTarget(LocalPlayer player, Level level, BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        BlockState state = level.getBlockState(clicked);
        if (!player.isSecondaryUseActive() && state.getMenuProvider(level, clicked) != null) {
            return null;
        }
        return state.canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
    }

    /**
     * The client places its own prediction of the block during the same call that sent the click,
     * so by the next click or the end of the tick the obsidian is either there or it never will be.
     * Only a placement that really happened starts the window.
     */
    private static void settlePending(Level level) {
        if (pendingTarget == null) {
            return;
        }
        if (level != null && level.getBlockState(pendingTarget).is(Blocks.OBSIDIAN)) {
            hasLast = true;
            lastPlacedAt = pendingAt;
            lastSlot = pendingSlot;
            switchedSinceLast = false;
        }
        pendingTarget = null;
    }

    private static void noteSlot(LocalPlayer player) {
        if (player != null && hasLast && lastSlot != OFFHAND
                && player.getInventory().getSelectedSlot() != lastSlot) {
            switchedSinceLast = true;
        }
    }

    private static int slotOf(LocalPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : OFFHAND;
    }

    /** Placements refused this session, for the benchmark report. */
    public static long refusedCount() {
        return refusedCount;
    }

    public static void reset() {
        hasLast = false;
        lastSlot = -1;
        switchedSinceLast = false;
        pendingTarget = null;
    }

    /** Forgets the window when the setting changes, so a new value never inherits an old click. */
    public static void settingsChanged() {
        reset();
    }
}
