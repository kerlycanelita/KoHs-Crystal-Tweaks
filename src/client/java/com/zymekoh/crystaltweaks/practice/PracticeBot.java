package com.zymekoh.crystaltweaks.practice;

import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.mixin.client.MannequinAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Crystal Practice opponent: a Vanilla mannequin, which already looks, wears armour, swings,
 * eats and pops totems like a player, driven by a small crystal PvP brain.
 *
 * <p>Everything it does goes through the same Vanilla code a player's action would reach on the
 * server: its crystals are real {@link EndCrystal}s, its hits real damage sources, its totems and
 * golden apples Vanilla's own consumption. Its armour comes from {@link PracticeKit#dress}, the
 * same call that dresses the player. It exists only in the practice world's integrated server.</p>
 */
final class PracticeBot {
    static final String TAG = "crystal_tweaks_practice_bot";
    private static final double PLACE_REACH = 4.5D;
    private static final double BREAK_REACH = 3.0D;
    private static final int SEARCH = 5;
    private static final double ARENA_LIMIT = 56.0D;

    private final ServerLevel level;
    private final PracticeSettings settings;
    private final Mannequin body;
    private final RandomSource random;
    private int totems = 16;
    private int apples = 48;
    private int actionCooldown = 30;
    private int totemTimer = -1;
    private int strafeTimer;
    private float strafe = 1F;
    private int meleeCooldown;
    private int eatCooldown;
    private boolean eating;
    private EndCrystal pending;
    private int pendingTimer;
    private float lastHealth;
    private int flinch;
    private int jumpTimer;

    private PracticeBot(ServerLevel level, PracticeSettings settings, Mannequin body) {
        this.level = level;
        this.settings = settings;
        this.body = body;
        this.random = level.getRandom();
        this.lastHealth = body.getHealth();
    }

    static PracticeBot spawn(ServerLevel level, PracticeSettings settings, double x, double y, double z, float yaw) {
        Mannequin body = EntityType.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
        if (body == null) {
            return null;
        }
        body.snapTo(x, y, z, yaw, 0.0F);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
        body.addTag(TAG);
        body.setCustomName(Component.literal("Crystal Bot"));
        body.setCustomNameVisible(true);
        ((MannequinAccessor) body).crystalTweaks$setDescription(
                Component.literal("Bot · " + settings.difficulty.label(CrystalUi.spanish())));
        PracticeKit.dress(body, level, settings);
        body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.END_CRYSTAL, 64));
        body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        level.addFreshEntity(body);
        return new PracticeBot(level, settings, body);
    }

    boolean alive() {
        return !this.body.isRemoved() && this.body.isAlive();
    }

    boolean removed() {
        return this.body.isRemoved();
    }

    Vec3 position() {
        return this.body.position();
    }

    void discard() {
        this.body.discard();
    }

    // ------------------------------------------------------------------------------------------
    // Movement: set before the entity ticks, so Vanilla's own travel moves it this tick.
    // ------------------------------------------------------------------------------------------

    void steer(ServerPlayer target) {
        if (!alive()) {
            return;
        }
        if (target == null) {
            this.body.zza = 0.0F;
            this.body.xxa = 0.0F;
            this.body.setJumping(false);
            return;
        }
        face(target.getEyePosition());
        Vec3 self = this.body.position();
        Vec3 toTarget = new Vec3(target.getX() - self.x, 0.0D, target.getZ() - self.z);
        double distance = toTarget.length();
        Vec3 toward = distance > 1.0E-4D ? toTarget.scale(1.0D / distance) : Vec3.ZERO;
        Vec3 side = new Vec3(-toward.z, 0.0D, toward.x);

        if (--this.strafeTimer <= 0) {
            this.strafe = this.random.nextFloat() < 0.5F ? -1F : 1F;
            this.strafeTimer = 15 + this.random.nextInt(30);
        }
        boolean retreating = effectiveHealth() < 9.0F && this.apples > 0;
        double approach = retreating ? -1.0D : distance > 4.4D ? 1.0D : distance < 2.4D ? -0.8D : 0.0D;
        Vec3 wanted = toward.scale(approach).add(side.scale(this.strafe * 0.75D)).add(avoidCrystals());
        if (Math.abs(self.x) > ARENA_LIMIT || Math.abs(self.z) > ARENA_LIMIT) {
            wanted = new Vec3(-self.x, 0.0D, -self.z).normalize();
        }
        if (wanted.lengthSqr() > 1.0D) {
            wanted = wanted.normalize();
        }
        // Minecraft moves an entity along its own facing: turn the wanted direction into the
        // forward and sideways inputs a player's keys would give.
        float yaw = this.body.getYRot() * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);
        this.body.zza = (float) (-wanted.x * sin + wanted.z * cos);
        this.body.xxa = (float) (wanted.x * cos + wanted.z * sin);
        boolean sprint = approach > 0.0D && distance > 5.5D;
        this.body.setSprinting(sprint);
        this.body.setSpeed(sprint ? 0.13F : 0.1F);

        if (this.jumpTimer > 0) {
            this.jumpTimer--;
        }
        boolean jump = this.body.onGround() && (this.body.horizontalCollision
                || (this.flinch > 0 && this.random.nextFloat() < 0.35F)
                || (this.jumpTimer == 0 && this.random.nextFloat() < 0.025F));
        if (jump) {
            this.jumpTimer = 12;
        }
        this.body.setJumping(jump);
    }

    /** Turns toward a point at a limited speed, like a hand on a mouse. */
    private void face(Vec3 point) {
        Vec3 eye = this.body.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        float wantedYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float wantedPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        float turn = 10.0F + 30.0F * this.settings.difficulty.precision;
        float yaw = this.body.getYRot() + Mth.clamp(Mth.wrapDegrees(wantedYaw - this.body.getYRot()), -turn, turn);
        float pitch = this.body.getXRot() + Mth.clamp(wantedPitch - this.body.getXRot(), -turn, turn);
        this.body.setYRot(yaw);
        this.body.setYHeadRot(yaw);
        this.body.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
    }

    /** A push away from crystals that would hurt the bot more than the player. */
    private Vec3 avoidCrystals() {
        Vec3 push = Vec3.ZERO;
        for (EndCrystal crystal : this.level.getEntitiesOfClass(EndCrystal.class, this.body.getBoundingBox().inflate(4.0D))) {
            float self = damage(crystal.position(), this.body);
            if (self >= 6.0F) {
                Vec3 away = this.body.position().subtract(crystal.position());
                away = new Vec3(away.x, 0.0D, away.z);
                double length = away.length();
                if (length > 1.0E-3D) {
                    push = push.add(away.scale(0.6D / length));
                }
            }
        }
        return push;
    }

    // ------------------------------------------------------------------------------------------
    // Actions: after the world tick, with the positions it produced.
    // ------------------------------------------------------------------------------------------

    void act(ServerPlayer target) {
        if (!alive()) {
            return;
        }
        float health = this.body.getHealth();
        if (health < this.lastHealth - 0.5F) {
            // Hit: flinch for a reaction time, and change direction the way a player does.
            this.flinch = this.settings.difficulty.reaction;
            this.strafe = -this.strafe;
        }
        this.lastHealth = health;
        if (this.flinch > 0) {
            this.flinch--;
        }
        keepTotem();
        if (eat()) {
            return;
        }
        if (target == null) {
            return;
        }
        breakPending();
        if (this.actionCooldown > 0) {
            this.actionCooldown--;
            return;
        }
        if (this.flinch > 0 && this.settings.difficulty.ordinal() < PracticeSettings.Difficulty.HARD.ordinal()) {
            return;
        }
        EndCrystal crystal = bestCrystalToBreak(target);
        if (crystal != null) {
            hold(new ItemStack(Items.END_CRYSTAL, 64));
            hit(crystal);
            this.actionCooldown = this.settings.difficulty.actionDelay;
            return;
        }
        BlockPos base = bestBase(target);
        if (base != null && placeCrystal(base)) {
            this.actionCooldown = this.settings.difficulty.actionDelay;
            return;
        }
        BlockPos obsidian = obsidianSpot(target);
        if (obsidian != null) {
            placeObsidian(obsidian);
            this.actionCooldown = this.settings.difficulty.actionDelay + 1;
            return;
        }
        melee(target);
    }

    private void keepTotem() {
        if (this.body.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.TOTEM_OF_UNDYING) || this.totems <= 0) {
            this.totemTimer = -1;
            return;
        }
        if (this.totemTimer < 0) {
            this.totemTimer = this.settings.difficulty.reaction;
        } else if (this.totemTimer-- == 0) {
            this.body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            this.totems--;
            this.totemTimer = -1;
        }
    }

    /** Eats a golden apple when hurt, through Vanilla's own item use: animation, sound, effects. */
    private boolean eat() {
        if (this.eating) {
            if (this.body.isUsingItem()) {
                return true;
            }
            this.eating = false;
            hold(new ItemStack(Items.END_CRYSTAL, 64));
        }
        if (this.eatCooldown > 0) {
            this.eatCooldown--;
            return false;
        }
        if (this.apples > 0 && effectiveHealth() < 12.0F && this.flinch == 0) {
            hold(new ItemStack(Items.GOLDEN_APPLE, 1));
            this.body.startUsingItem(InteractionHand.MAIN_HAND);
            if (this.body.isUsingItem()) {
                this.eating = true;
                this.apples--;
                this.eatCooldown = 50;
                return true;
            }
        }
        return false;
    }

    private void breakPending() {
        if (this.pending == null) {
            return;
        }
        if (this.pending.isRemoved()) {
            this.pending = null;
            return;
        }
        if (--this.pendingTimer <= 0) {
            if (inBreakReach(this.pending)) {
                hit(this.pending);
            }
            this.pending = null;
        }
    }

    private void hit(EndCrystal crystal) {
        swingArm();
        // The same call a player's hit reaches on the server; the explosion is credited to the bot.
        crystal.hurtServer(this.level, this.level.damageSources().mobAttack(this.body), 1.0F);
    }

    private EndCrystal bestCrystalToBreak(ServerPlayer target) {
        EndCrystal best = null;
        float bestScore = 0.0F;
        float minimum = Math.max(2.0F, this.settings.difficulty.minimumDamage * 0.6F);
        for (EndCrystal crystal : this.level.getEntitiesOfClass(EndCrystal.class,
                this.body.getBoundingBox().inflate(BREAK_REACH + 1.0D))) {
            if (crystal.isRemoved() || !inBreakReach(crystal)) {
                continue;
            }
            float toTarget = damage(crystal.position(), target);
            float toSelf = damage(crystal.position(), this.body);
            if (toTarget < minimum || toSelf >= effectiveHealth() - 1.0F || toSelf > toTarget * 1.2F) {
                continue;
            }
            float score = toTarget - toSelf * 0.5F;
            if (score > bestScore) {
                bestScore = score;
                best = crystal;
            }
        }
        return best;
    }

    private BlockPos bestBase(ServerPlayer target) {
        BlockPos feet = target.blockPosition();
        Vec3 eye = this.body.getEyePosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -SEARCH; dx <= SEARCH; dx++) {
            for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos base = feet.offset(dx, dy, dz);
                    if (!crystalBase(this.level.getBlockState(base)) || !this.level.isEmptyBlock(base.above())) {
                        continue;
                    }
                    if (eye.distanceToSqr(Vec3.atCenterOf(base).add(0.0D, 0.5D, 0.0D)) > PLACE_REACH * PLACE_REACH) {
                        continue;
                    }
                    candidates.add(base.immutable());
                }
            }
        }
        // Cheap order first; the ray-traced damage only for the handful that can matter.
        Vec3 targetFeet = target.position();
        candidates.sort(Comparator.comparingDouble(base -> Vec3.atCenterOf(base).distanceToSqr(targetFeet)));
        List<BlockPos> ranked = new ArrayList<>();
        List<Float> scores = new ArrayList<>();
        int evaluated = 0;
        for (BlockPos base : candidates) {
            if (evaluated++ >= 12) {
                break;
            }
            BlockPos above = base.above();
            AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0D, above.getY() + 2.0D,
                    above.getZ() + 1.0D);
            if (!this.level.getEntities(null, space).isEmpty()) {
                continue;
            }
            Vec3 center = new Vec3(above.getX() + 0.5D, above.getY(), above.getZ() + 0.5D);
            float toTarget = damage(center, target);
            float toSelf = damage(center, this.body);
            if (toTarget < this.settings.difficulty.minimumDamage || toSelf >= effectiveHealth() - 1.0F
                    || toSelf > toTarget) {
                continue;
            }
            ranked.add(base);
            scores.add(toTarget - toSelf * 0.6F);
        }
        if (ranked.isEmpty()) {
            return null;
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            order.add(index);
        }
        order.sort((first, second) -> Float.compare(scores.get(second), scores.get(first)));
        // A less precise bot sometimes settles for its second or third choice, as people do.
        int pick = 0;
        if (order.size() > 1 && this.random.nextFloat() > this.settings.difficulty.precision) {
            pick = 1 + this.random.nextInt(Math.min(2, order.size() - 1));
        }
        return ranked.get(order.get(pick));
    }

    private static boolean crystalBase(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    /** Places a crystal the way Vanilla's crystal item does on the server. */
    private boolean placeCrystal(BlockPos base) {
        BlockPos above = base.above();
        if (!this.level.isEmptyBlock(above)) {
            return false;
        }
        AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0D, above.getY() + 2.0D,
                above.getZ() + 1.0D);
        if (!this.level.getEntities(null, space).isEmpty()) {
            return false;
        }
        face(Vec3.atCenterOf(base).add(0.0D, 0.5D, 0.0D));
        hold(new ItemStack(Items.END_CRYSTAL, 64));
        EndCrystal crystal = new EndCrystal(this.level, above.getX() + 0.5D, above.getY(), above.getZ() + 0.5D);
        crystal.setShowBottom(false);
        this.level.addFreshEntity(crystal);
        swingArm();
        this.pending = crystal;
        this.pendingTimer = this.settings.difficulty.breakDelay;
        return true;
    }

    /**
     * When the player stands where no obsidian is in reach, the bot builds its own: a block beside
     * the player's feet, which the next crystal can then stand on.
     */
    private BlockPos obsidianSpot(ServerPlayer target) {
        BlockPos feet = target.blockPosition();
        if (crystalBase(this.level.getBlockState(feet.below()))) {
            return null;
        }
        Vec3 eye = this.body.getEyePosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos spot = feet.relative(direction);
            if (!this.level.getBlockState(spot).canBeReplaced()
                    || this.level.getBlockState(spot.below()).canBeReplaced()
                    || eye.distanceToSqr(Vec3.atCenterOf(spot)) > PLACE_REACH * PLACE_REACH) {
                continue;
            }
            AABB space = new AABB(spot);
            if (this.level.getEntities(null, space).isEmpty()) {
                return spot;
            }
        }
        return null;
    }

    private void placeObsidian(BlockPos spot) {
        face(Vec3.atCenterOf(spot));
        hold(new ItemStack(Items.OBSIDIAN, 64));
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        this.level.setBlock(spot, obsidian, 3);
        this.level.playSound(null, spot, obsidian.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        swingArm();
    }

    private void melee(ServerPlayer target) {
        if (this.meleeCooldown > 0) {
            this.meleeCooldown--;
            return;
        }
        if (this.body.distanceToSqr(target) > 3.0D * 3.0D) {
            return;
        }
        hold(PracticeKit.sword(this.level, this.settings));
        swingArm();
        float damage = switch (this.settings.armor) {
            case NETHERITE -> 11.0F;
            case DIAMOND -> 10.0F;
            case IRON -> 9.0F;
        };
        target.hurtServer(this.level, this.level.damageSources().mobAttack(this.body), damage);
        // A 1.6 attack-speed sword recharges in 12.5 ticks.
        this.meleeCooldown = 13;
    }

    /** The arm swing every player-side action shows, broadcast to whoever is watching. */
    private void swingArm() {
        this.body.swing(InteractionHand.MAIN_HAND);
    }

    private void hold(ItemStack stack) {
        ItemStack held = this.body.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!held.is(stack.getItem())) {
            this.body.setItemSlot(EquipmentSlot.MAINHAND, stack);
        }
    }

    private boolean inBreakReach(EndCrystal crystal) {
        return crystal.getBoundingBox().distanceToSqr(this.body.getEyePosition()) <= BREAK_REACH * BREAK_REACH;
    }

    private float effectiveHealth() {
        return this.body.getHealth() + this.body.getAbsorptionAmount();
    }

    /**
     * What a crystal exploding at {@code center} would take from {@code entity}: Vanilla's
     * explosion formula for a power-6 blast, then armour, then the enchantment protection the kit
     * gives. The kit is the same for both sides, which is what keeps the comparison fair.
     */
    private float damage(Vec3 center, LivingEntity entity) {
        double distance = Math.sqrt(entity.distanceToSqr(center)) / 12.0D;
        if (distance > 1.0D) {
            return 0.0F;
        }
        double impact = (1.0D - distance) * ServerExplosion.getSeenPercent(center, entity);
        float damage = (float) ((impact * impact + impact) / 2.0D * 7.0D * 12.0D + 1.0D);
        float armor = entity.getArmorValue();
        float toughness = (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float absorbed = Mth.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F);
        damage *= 1.0F - absorbed / 25.0F;
        int blastPieces = Integer.bitCount(this.settings.blastPieces);
        int protection = Math.min(20, blastPieces * 8 + (4 - blastPieces) * 4);
        return damage * (1.0F - protection / 25.0F);
    }
}
