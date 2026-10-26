package com.zymekoh.crystaltweaks.practice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The damage map, a Crystal Practice tool only: every obsidian or bedrock within your reach where a
 * crystal would fit gets a light, sized by what its blast would do to the bot and coloured by what it
 * would do to you. Violet: worth it. Amber: it hurts you nearly as much. Crimson: it hurts you more, or
 * would take your last health. The best spot gets a spark above it.
 *
 * <p>It is worked out on the practice world's own server, with the same blast maths the bot uses,
 * armour and protection included, and shown with particles only the player sees. It never exists on
 * a real server: there this would be information no player has.</p>
 */
final class DamageMap {
    /** Every this many ticks the map is worked out again. */
    static final int PERIOD = 10;
    private static final double REACH = 4.5D;
    private static final int MAX_SPOTS = 24;
    private static final float CRYSTAL_POWER = 6.0F;
    private static final int GOOD = 0xC880FF;
    private static final int EVEN = 0xFFC48A;
    private static final int BAD = 0xFF315C;

    private record Spot(BlockPos base, float toBot, float toYou) {
    }

    private DamageMap() {
    }

    static void show(ServerLevel level, ServerPlayer player, LivingEntity bot, PracticeSettings settings) {
        if (player == null || bot == null || !bot.isAlive()) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        BlockPos center = player.blockPosition();
        List<BlockPos> bases = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -3, -4), center.offset(4, 2, 4))) {
            if (eye.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > REACH * REACH) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if ((state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)) && fits(level, pos)) {
                bases.add(pos.immutable());
            }
        }
        // The spots nearest the bot first: those are the ones that matter.
        Vec3 target = bot.position();
        bases.sort(Comparator.comparingDouble(pos -> target.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D)));
        Spot best = null;
        List<Spot> spots = new ArrayList<>();
        for (BlockPos base : bases) {
            if (spots.size() >= MAX_SPOTS) {
                break;
            }
            Vec3 blast = new Vec3(base.getX() + 0.5D, base.getY() + 1.0D, base.getZ() + 0.5D);
            float toBot = PracticeCombat.explosionDamage(bot, blast, CRYSTAL_POWER, bot.position(), settings);
            if (toBot < 1.0F) {
                continue;
            }
            float toYou = PracticeCombat.explosionDamage(player, blast, CRYSTAL_POWER, player.position(), settings);
            Spot spot = new Spot(base, toBot, toYou);
            spots.add(spot);
            if (toYou < player.getHealth() + player.getAbsorptionAmount() && (best == null || toBot - toYou > best.toBot() - best.toYou())) {
                best = spot;
            }
        }
        float life = player.getHealth() + player.getAbsorptionAmount();
        for (Spot spot : spots) {
            int colour = spot.toYou() >= life || spot.toYou() >= spot.toBot() ? BAD : spot.toYou() * 1.5F > spot.toBot() ? EVEN : GOOD;
            float size = Math.min(2.5F, 0.6F + spot.toBot() / 8.0F);
            level.sendParticles(player, new DustParticleOptions(colour, size), true, true, spot.base().getX() + 0.5D,
                    spot.base().getY() + 1.1D, spot.base().getZ() + 0.5D, 2, 0.08D, 0.02D, 0.08D, 0.0D);
        }
        if (best != null) {
            level.sendParticles(player, ParticleTypes.END_ROD, true, true, best.base().getX() + 0.5D, best.base().getY() + 1.6D,
                    best.base().getZ() + 0.5D, 1, 0.0D, 0.1D, 0.0D, 0.0D);
        }
    }

    /** What Vanilla asks before it places a crystal: air above, and nothing in the two blocks it fills. */
    private static boolean fits(ServerLevel level, BlockPos base) {
        BlockPos above = base.above();
        if (!level.isEmptyBlock(above)) {
            return false;
        }
        AABB room = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0D, above.getY() + 2.0D, above.getZ() + 1.0D);
        return level.getEntities(null, room).isEmpty();
    }
}
