package com.zymekoh.crystaltweaks.practice;

import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.mixin.client.MannequinAccessor;
import com.zymekoh.crystaltweaks.mixin.client.RespawnAnchorInvoker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The Crystal Practice opponent: a Vanilla mannequin, which already looks, wears armour, swings,
 * eats and pops totems like a player, driven by a crystal PvP brain.
 *
 * <p>Everything it does goes through the same Vanilla code a player's action would reach on the
 * server: its crystals are real {@link EndCrystal}s, its anchors real anchors set off by Vanilla's
 * own explosion, its hits real damage with a Knockback sword's knockback, its totems and golden
 * apples Vanilla's own consumption. It carries the player's own kit: the same armour, sword and
 * number of totems, apples and pearls. It exists only in the practice world's integrated server.</p>
 *
 * <p>What it can do depends on the difficulty and is listed by {@link BotSkill}; none of it can be
 * switched off. The fastest timings are the ones players reach by hand: obsidian, crystal and the
 * hit on consecutive ticks, an anchor placed, charged and set off in three.</p>
 */
final class PracticeBot {
    static final String TAG = "crystal_tweaks_practice_bot";
    private static final double PLACE_REACH = 4.5D;
    private static final double HIT_REACH = 3.0D;
    private static final int SEARCH = 4;
    /** Spots whose damage is ray-traced each decision; the rest are ruled out by distance. */
    private static final int CANDIDATES = 12;
    /** A 1.6 attack-speed sword recharges in 12.5 ticks. */
    private static final int MELEE_RECHARGE = 13;
    private static final float AIM_TOLERANCE = 35.0F;
    private static final int COMBO_TIMEOUT = 80;

    private enum Kind { CRYSTAL, HIT_CRYSTAL, ANCHOR }

    private enum Step { HIT, OBSIDIAN, CRYSTAL, BREAK, WAIT, ANCHOR, CHARGE, SHIELD, DETONATE }

    /** A combo in progress: what comes next and where. */
    private static final class Combo {
        final Kind kind;
        Step step;
        Step afterWait;
        int wait;
        int age;
        BlockPos base;
        BlockPos anchor;
        BlockPos shield;
        EndCrystal crystal;
        int taps;
        int blasts;
        boolean stacked;
        Vec3 focus;

        Combo(Kind kind, Step step) {
            this.kind = kind;
            this.step = step;
        }
    }

    /** A place to put a crystal or an anchor, and what it is worth. */
    private record Option(BlockPos pos, boolean needsObsidian, boolean anchor, float toTarget, float toSelf, float score) {
    }

    private final ServerLevel level;
    private final PracticeSettings settings;
    private final PracticeSettings.Difficulty difficulty;
    private final Mannequin body;
    private final ItemStack sword;
    private final RandomSource random;
    private int totems;
    private int apples;
    private int pearls;

    private int age;
    private int actionCooldown = 30;
    private int totemTimer = -1;
    private int meleeCooldown;
    private int eatCooldown;
    private int pearlCooldown;
    private int mendCooldown;
    private int sprintReset;
    private boolean eating;
    private float lastHealth;
    private int flinch;
    private int strafeTimer;
    private float strafe = 1.0F;
    private int jumpTimer;

    private Vec3 targetLast;
    private Vec3 targetVelocity = Vec3.ZERO;
    private boolean targetHeldTotem;
    private int poppedAt = -1000;
    private int knockedAt = -1000;
    private Vec3 knockOrigin = Vec3.ZERO;
    private Vec3 knockVelocity = Vec3.ZERO;
    private Combo combo;
    private BlockPos refuge;
    /** Ticks before looking for a new combo again after finding nothing worth doing. */
    private int searchCooldown;
    private final boolean aggressive;
    /** Ticks it stays behind a block it just placed to cover itself. */
    private int holdTicks;
    private int blockOffCooldown;
    /** Whether steering asked to move this tick, and for how long that has gone nowhere. */
    private boolean wantsMove;
    private int stuckTicks;
    private Vec3 lastPosition;
    private BlockPos miningPos;
    private float miningProgress;
    /** Its last pearl, until it lands: one pearl at a time, as a player waits for theirs. */
    private ThrownEnderpearl pearlInFlight;

    private PracticeBot(ServerLevel level, PracticeSettings settings, Mannequin body, ItemStack sword, KitLayout kit) {
        this.level = level;
        this.settings = settings;
        this.difficulty = settings.difficulty;
        this.aggressive = settings.style == PracticeSettings.BotStyle.AGGRESSIVE;
        this.body = body;
        this.sword = sword;
        this.random = level.getRandom();
        this.lastHealth = body.getHealth();
        // The same kit as the player: one totem already in the off hand, the rest carried.
        this.totems = Math.max(0, kit.totems() - 1);
        this.apples = kit.count(KitItem.GOLDEN_APPLE);
        this.pearls = kit.count(KitItem.ENDER_PEARL);
    }

    static PracticeBot spawn(ServerLevel level, PracticeSettings settings, KitLayout kit, double x, double y, double z,
            float yaw) {
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
        boolean spanish = CrystalUi.spanish();
        ((MannequinAccessor) body).crystalTweaks$setDescription(Component.literal(
                "Bot · " + settings.difficulty.label(spanish) + " · " + settings.style.label(spanish)));
        PracticeKit.dress(body, level, settings);
        ItemStack sword = PracticeKit.sword(level, settings);
        body.setItemSlot(EquipmentSlot.MAINHAND, sword);
        if (kit.totems() > 0) {
            body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
        level.addFreshEntity(body);
        return new PracticeBot(level, settings, body, sword, kit);
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

    private boolean has(BotSkill skill) {
        return this.settings.uses(skill);
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
            this.body.setSprinting(false);
            this.wantsMove = false;
            return;
        }
        if (this.miningPos != null) {
            // Mining its way out: it stands still, looking at the block.
            face(Vec3.atCenterOf(this.miningPos));
            this.body.zza = 0.0F;
            this.body.xxa = 0.0F;
            this.body.setJumping(false);
            this.body.setSprinting(false);
            this.wantsMove = false;
            return;
        }
        Combo current = this.combo;
        Vec3 look = current != null && current.focus != null ? current.focus : target.getEyePosition();
        Vec3 self = this.body.position();
        Vec3 flat = new Vec3(target.getX() - self.x, 0.0D, target.getZ() - self.z);
        double distance = flat.length();
        Vec3 toward = distance > 1.0E-4D ? flat.scale(1.0D / distance) : Vec3.ZERO;
        Vec3 side = new Vec3(-toward.z, 0.0D, toward.x);

        if (--this.strafeTimer <= 0) {
            this.strafe = this.random.nextFloat() < 0.5F ? -1.0F : 1.0F;
            this.strafeTimer = 15 + this.random.nextInt(30);
        }
        float health = PracticeCombat.effectiveHealth(this.body);
        boolean low = health < 10.0F;
        BlockPos hole = seekRefuge(low || this.eating);
        Vec3 wanted;
        boolean sprint = false;
        boolean retreating = false;
        if (hole != null) {
            Vec3 to = Vec3.atBottomCenterOf(hole).subtract(self);
            wanted = new Vec3(to.x, 0.0D, to.z);
            wanted = wanted.lengthSqr() < 0.04D ? Vec3.ZERO : wanted.normalize();
            retreating = true;
        } else if (!this.aggressive && (this.eating || (low && this.apples > 0 && distance < 6.0D))) {
            retreating = true;
            if (this.eating) {
                // Eating slows it to a walk: it sidesteps away while it keeps an eye on you.
                wanted = toward.scale(-0.7D).add(side.scale(this.strafe * 0.5D));
            } else {
                // It turns and runs rather than hopping backwards: nobody sprints in reverse.
                wanted = toward.scale(-1.0D).add(side.scale(this.strafe * 0.25D)).normalize();
                look = this.body.getEyePosition().add(wanted.scale(4.0D));
                sprint = this.sprintReset <= 0;
            }
        } else if (this.aggressive) {
            // Rush: always on top of you, never a step back.
            double ideal = wantsToHit() ? 1.8D : 2.4D;
            double approach = distance > ideal + 0.4D ? 1.0D : 0.0D;
            wanted = toward.scale(approach).add(side.scale(this.strafe * 0.35D));
            sprint = approach > 0.0D && distance > 2.6D && this.sprintReset <= 0;
        } else if (this.holdTicks > 0) {
            // Behind the block it just placed: stepping out from behind it would waste it.
            wanted = Vec3.ZERO;
        } else {
            double ideal = wantsToHit() ? 2.2D : 3.4D;
            double approach = distance > ideal + 0.8D ? 1.0D : distance < ideal - 0.8D ? -0.7D : 0.0D;
            wanted = toward.scale(approach).add(side.scale(this.strafe * 0.7D));
            sprint = approach > 0.0D && distance > 3.2D && this.sprintReset <= 0;
        }
        face(look);
        wanted = wanted.add(avoidCrystals().scale(this.aggressive ? 0.4D : 1.0D));
        double limit = PracticeArena.RADIUS - 4;
        if (Math.abs(self.x) > limit || Math.abs(self.z) > limit) {
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
        // Like a player, it only sprints moving forward.
        sprint = sprint && this.body.zza > 0.6F;
        this.body.setSprinting(sprint);
        this.body.setSpeed(sprint ? 0.13F : 0.1F);
        this.wantsMove = wanted.lengthSqr() > 0.04D;

        if (this.jumpTimer > 0) {
            this.jumpTimer--;
        }
        // Retreating it only jumps to clear an obstacle: no hopping away.
        boolean critJump = !retreating && has(BotSkill.CRITS) && distance <= 3.6D && this.meleeCooldown <= 5
                && PracticeCombat.immunity(target) <= 10;
        boolean jump = this.body.onGround() && (this.body.horizontalCollision
                || critJump
                || (!retreating && this.flinch > 0 && this.random.nextFloat() < 0.35F)
                || (!retreating && this.jumpTimer == 0 && this.random.nextFloat() < 0.025F));
        if (jump) {
            this.jumpTimer = 12;
        }
        this.body.setJumping(jump);
    }

    private boolean wantsToHit() {
        return has(BotSkill.HIT_CRYSTAL) && this.meleeCooldown <= 3
                && (this.combo == null || this.combo.step == Step.HIT);
    }

    /** Turns toward a point at a limited speed, like a hand on a mouse. */
    private void face(Vec3 point) {
        Vec3 eye = this.body.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        float wantedYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float wantedPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        float turn = 10.0F + 30.0F * this.difficulty.precision;
        float yaw = this.body.getYRot() + Mth.clamp(Mth.wrapDegrees(wantedYaw - this.body.getYRot()), -turn, turn);
        float pitch = this.body.getXRot() + Mth.clamp(wantedPitch - this.body.getXRot(), -turn, turn);
        this.body.setYRot(yaw);
        this.body.setYHeadRot(yaw);
        this.body.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
    }

    /** Turns toward a point and says whether it is now looking close enough to click it. */
    private boolean aim(Vec3 point) {
        face(point);
        Vec3 eye = this.body.getEyePosition();
        Vec3 look = this.body.getViewVector(1.0F);
        Vec3 to = point.subtract(eye);
        double length = to.length();
        if (length < 1.0E-3D) {
            return true;
        }
        double cosine = look.dot(to.scale(1.0D / length));
        return cosine >= Math.cos(AIM_TOLERANCE * Mth.DEG_TO_RAD);
    }

    /** A push away from crystals that would hurt the bot badly. */
    private Vec3 avoidCrystals() {
        Vec3 push = Vec3.ZERO;
        for (EndCrystal crystal : this.level.getEntitiesOfClass(EndCrystal.class, this.body.getBoundingBox().inflate(4.0D))) {
            if (this.combo != null && crystal == this.combo.crystal) {
                continue;
            }
            float self = PracticeCombat.explosionDamage(this.body, crystal.position(), PracticeCombat.CRYSTAL_POWER,
                    this.body.position(), this.settings);
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

    /** A one-block hole nearby to drop into when low, for bots that know to. */
    private BlockPos seekRefuge(boolean wanted) {
        if (!wanted || !has(BotSkill.HOLES)) {
            this.refuge = null;
            return null;
        }
        if (this.refuge != null && isHole(this.refuge)) {
            return this.refuge;
        }
        BlockPos feet = this.body.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    BlockPos spot = feet.offset(dx, dy, dz);
                    if (!isHole(spot)) {
                        continue;
                    }
                    double distance = spot.distSqr(feet);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = spot;
                    }
                }
            }
        }
        this.refuge = best;
        return best;
    }

    private boolean isHole(BlockPos feet) {
        if (!this.level.isEmptyBlock(feet) || !this.level.isEmptyBlock(feet.above())
                || !blastProof(this.level.getBlockState(feet.below()))) {
            return false;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!blastProof(this.level.getBlockState(feet.relative(direction)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean blastProof(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK) || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.NETHERITE_BLOCK) || state.is(Blocks.RESPAWN_ANCHOR);
    }

    // ------------------------------------------------------------------------------------------
    // Decisions: after the world tick, with the positions it produced.
    // ------------------------------------------------------------------------------------------

    void act(ServerPlayer target) {
        if (!alive()) {
            return;
        }
        this.age++;
        tickTimers();
        senseSelf();
        keepTotem();
        if (target == null || !target.isAlive()) {
            this.combo = null;
            return;
        }
        senseTarget(target);
        trackStuck();
        if (mineOut(target)) {
            this.combo = null;
            return;
        }
        if (retreatWithPearl(target)) {
            this.combo = null;
            return;
        }
        if (eat(target)) {
            this.combo = null;
            return;
        }
        if (this.actionCooldown > 0) {
            this.actionCooldown--;
            return;
        }
        if (this.flinch > 0 && !has(BotSkill.DOUBLE_TAP)) {
            // Below Hard a hit staggers it for its reaction time, the way a flinch costs a player.
            return;
        }
        if (snipeCrystal(target)) {
            this.actionCooldown = this.combo != null ? 0 : this.difficulty.actionDelay - 1;
            return;
        }
        if (this.combo != null) {
            int result = continueCombo(target);
            if (result > 0 || this.combo != null) {
                // A combo times its own steps; mid-combo nothing else interrupts it.
                return;
            }
        }
        boolean acted = defend(target);
        if (!acted && this.searchCooldown > 0) {
            this.searchCooldown--;
        } else if (!acted) {
            acted = startCombo(target);
            if (this.combo != null) {
                return;
            }
            if (!acted) {
                this.searchCooldown = 3;
            }
        }
        if (!acted) {
            acted = chase(target) || melee(target);
        }
        if (acted) {
            this.actionCooldown = this.difficulty.actionDelay - 1;
        }
    }

    private void tickTimers() {
        if (this.meleeCooldown > 0) {
            this.meleeCooldown--;
        }
        if (this.pearlCooldown > 0) {
            this.pearlCooldown--;
        }
        if (this.mendCooldown > 0) {
            this.mendCooldown--;
        }
        if (this.sprintReset > 0) {
            this.sprintReset--;
        }
        if (this.holdTicks > 0) {
            this.holdTicks--;
        }
        if (this.blockOffCooldown > 0) {
            this.blockOffCooldown--;
        }
    }

    /** Counts the ticks it has been walking into something without getting anywhere. */
    private void trackStuck() {
        Vec3 now = this.body.position();
        if (this.lastPosition != null && this.wantsMove && this.body.horizontalCollision
                && now.subtract(this.lastPosition).horizontalDistanceSqr() < 4.0E-4D) {
            this.stuckTicks++;
        } else if (this.stuckTicks > 0) {
            this.stuckTicks--;
        }
        this.lastPosition = now;
    }

    private void senseSelf() {
        float health = this.body.getHealth();
        if (health < this.lastHealth - 0.5F) {
            // Hit: flinch for a reaction time, and change direction the way a player does.
            this.flinch = this.difficulty.reaction;
            this.strafe = -this.strafe;
        }
        this.lastHealth = health;
        if (this.flinch > 0) {
            this.flinch--;
        }
    }

    private void senseTarget(ServerPlayer target) {
        Vec3 position = target.position();
        if (this.targetLast != null) {
            this.targetVelocity = position.subtract(this.targetLast);
        }
        this.targetLast = position;
        boolean holding = PracticeCombat.holdsTotem(target);
        if (this.targetHeldTotem && !holding && target.getHealth() <= 2.0F) {
            this.poppedAt = this.age;
        }
        this.targetHeldTotem = holding;
    }

    /** Right after the player's totem popped: the window a chain pop kills in. */
    private boolean chainWindow() {
        return has(BotSkill.CHAIN_POP) && this.age - this.poppedAt < 20;
    }

    private float minimumDamage() {
        if (chainWindow()) {
            return 1.0F;
        }
        // A rusher takes the smaller hits too rather than wait for the perfect one.
        return this.aggressive ? this.difficulty.minimumDamage * 0.8F : this.difficulty.minimumDamage;
    }

    private void keepTotem() {
        if (this.body.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.TOTEM_OF_UNDYING) || this.totems <= 0) {
            this.totemTimer = -1;
            return;
        }
        if (this.totemTimer < 0) {
            this.totemTimer = this.difficulty.reaction;
        } else if (this.totemTimer-- == 0) {
            this.body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            this.totems--;
            this.totemTimer = -1;
        }
    }

    /** Eats a golden apple when hurt, through Vanilla's own item use: animation, sound, effects. */
    private boolean eat(ServerPlayer target) {
        if (this.eating) {
            if (this.body.isUsingItem()) {
                return true;
            }
            this.eating = false;
            hold(this.sword);
        }
        if (this.eatCooldown > 0) {
            this.eatCooldown--;
            return false;
        }
        if (this.apples <= 0 || this.flinch > 0) {
            return false;
        }
        float health = PracticeCombat.effectiveHealth(this.body);
        double distance = this.body.distanceTo(target);
        if (this.aggressive) {
            // It eats where it stands, only when it has to, and keeps pushing while it does.
            if (health >= 8.0F) {
                return false;
            }
            return startEating();
        }
        boolean hurt = health < 12.0F;
        // The best keep their absorption up between exchanges, not only when already in trouble.
        boolean topUp = has(BotSkill.CHAIN_POP) && this.body.getAbsorptionAmount() <= 0.0F && health < 18.0F
                && distance > 6.0D;
        if (!hurt && !topUp) {
            return false;
        }
        // Safe to eat while out of reach, or while the player is still flying from its last hit.
        boolean safe = distance > 5.0D || PracticeCombat.immunity(target) > 10;
        if (health >= 8.0F && !safe) {
            return false;
        }
        return startEating();
    }

    private boolean startEating() {
        hold(new ItemStack(Items.GOLDEN_APPLE, 1));
        this.body.startUsingItem(InteractionHand.MAIN_HAND);
        if (this.body.isUsingItem()) {
            this.eating = true;
            this.apples--;
            this.eatCooldown = 40;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // Combos
    // ------------------------------------------------------------------------------------------

    /**
     * Starts the best combo available: a hit-crystal when the player stands close, otherwise a
     * crystal or an anchor wherever it would hurt the player most for the least risk to itself.
     */
    private boolean startCombo(ServerPlayer target) {
        float minimum = minimumDamage();
        boolean targetInHole = isHole(target.blockPosition());
        if (has(BotSkill.HIT_CRYSTAL) && !targetInHole && target.onGround() && this.body.distanceTo(target) <= 3.6D
                && this.meleeCooldown <= this.difficulty.step && PracticeCombat.immunity(target) <= 10
                && this.random.nextFloat() < hitCrystalChance()) {
            this.combo = new Combo(Kind.HIT_CRYSTAL, Step.HIT);
            this.combo.focus = target.getEyePosition();
            return continueCombo(target) > 0;
        }
        Option crystal = targetInHole && !has(BotSkill.FACEPLACE) ? null
                : bestCrystalOption(target, minimum, true);
        Option anchor = has(BotSkill.ANCHORS) ? bestAnchorOption(target, minimum) : null;
        Option chosen = crystal;
        if (anchor != null && (crystal == null || anchor.score() > crystal.score() * (targetInHole ? 0.7F : 1.15F))) {
            chosen = anchor;
        }
        if (chosen == null) {
            return false;
        }
        if (chosen.anchor()) {
            this.combo = new Combo(Kind.ANCHOR, Step.ANCHOR);
            this.combo.anchor = chosen.pos();
            this.combo.shield = has(BotSkill.SAFE_ANCHOR) ? shieldSpot(chosen.pos()) : null;
            this.combo.focus = Vec3.atCenterOf(chosen.pos());
        } else {
            this.combo = new Combo(Kind.CRYSTAL, chosen.needsObsidian() ? Step.OBSIDIAN : Step.CRYSTAL);
            this.combo.base = chosen.pos();
            this.combo.focus = topFace(chosen.pos());
        }
        return continueCombo(target) > 0;
    }

    private float hitCrystalChance() {
        if (this.aggressive) {
            return switch (this.difficulty) {
                case EASY -> 0.0F;
                case NORMAL -> 0.8F;
                case HARD -> 0.95F;
                case EXTREME -> 1.0F;
            };
        }
        return switch (this.difficulty) {
            case EASY -> 0.0F;
            case NORMAL -> 0.45F;
            case HARD -> 0.7F;
            case EXTREME -> 0.85F;
        };
    }

    /** @return 1 when it acted, 0 when it is waiting for the right moment, -1 when the combo ended */
    private int continueCombo(ServerPlayer target) {
        Combo current = this.combo;
        current.age++;
        if (current.age > COMBO_TIMEOUT || this.body.distanceTo(target) > 9.0D) {
            this.combo = null;
            return -1;
        }
        if (current.wait > 0) {
            current.wait--;
            return 0;
        }
        return switch (current.step) {
            case HIT -> stepHit(target, current);
            case OBSIDIAN -> stepObsidian(target, current);
            case CRYSTAL -> stepCrystal(target, current);
            case BREAK -> stepBreak(target, current);
            case WAIT -> stepWait(target, current);
            case ANCHOR -> stepAnchor(target, current);
            case CHARGE -> stepCharge(current);
            case SHIELD -> stepShield(current);
            case DETONATE -> stepDetonate(target, current);
        };
    }

    private int stepHit(ServerPlayer target, Combo current) {
        if (current.age > 40) {
            this.combo = null;
            return -1;
        }
        current.focus = target.getEyePosition();
        if (!melee(target)) {
            // Still closing in, or the sword is still recharging.
            return 0;
        }
        // The player is in the air now: the crystal goes where they will be when it is hit.
        Option follow = bestCrystalOption(target, this.difficulty.minimumDamage * 0.5F, true);
        if (follow == null) {
            this.combo = null;
            return 1;
        }
        current.base = follow.pos();
        current.focus = topFace(follow.pos());
        current.step = follow.needsObsidian() ? Step.OBSIDIAN : Step.CRYSTAL;
        current.wait = this.difficulty.step - 1;
        return 1;
    }

    private int stepObsidian(ServerPlayer target, Combo current) {
        if (!canPlaceBlock(current.base) || !this.level.isEmptyBlock(current.base.above())) {
            Option retry = bestCrystalOption(target, minimumDamage() * 0.5F, true);
            if (retry == null) {
                this.combo = null;
                return -1;
            }
            current.base = retry.pos();
            if (!retry.needsObsidian()) {
                current.step = Step.CRYSTAL;
                return stepCrystal(target, current);
            }
        }
        current.focus = Vec3.atCenterOf(current.base);
        if (!aim(current.focus)) {
            return 0;
        }
        placeBlock(current.base, Blocks.OBSIDIAN.defaultBlockState(), new ItemStack(Items.OBSIDIAN, 64));
        current.step = Step.CRYSTAL;
        current.focus = topFace(current.base);
        current.wait = this.difficulty.step - 1;
        return 1;
    }

    private int stepCrystal(ServerPlayer target, Combo current) {
        if (!crystalBase(this.level.getBlockState(current.base)) || !crystalSpaceFree(current.base)) {
            Option retry = bestCrystalOption(target, minimumDamage() * 0.5F, false);
            if (retry == null) {
                this.combo = null;
                return -1;
            }
            current.base = retry.pos();
        }
        current.focus = topFace(current.base);
        if (!aim(current.focus)) {
            return 0;
        }
        EndCrystal crystal = placeCrystal(current.base);
        if (crystal == null) {
            this.combo = null;
            return -1;
        }
        current.crystal = crystal;
        current.focus = crystal.position().add(0.0D, 0.5D, 0.0D);
        current.step = Step.BREAK;
        current.wait = this.difficulty.step - 1;
        return 1;
    }

    private int stepBreak(ServerPlayer target, Combo current) {
        EndCrystal crystal = current.crystal;
        if (crystal == null || crystal.isRemoved()) {
            // Someone broke it first; still counts as a tap for what follows.
            return followUp(target, current) ? 0 : -1;
        }
        if (!inBreakReach(crystal) || !safeToDetonate(crystal.position(), PracticeCombat.CRYSTAL_POWER, target)) {
            this.combo = null;
            return -1;
        }
        if (!aim(current.focus)) {
            return 0;
        }
        hit(crystal);
        current.taps++;
        followUp(target, current);
        return 1;
    }

    /**
     * After a crystal: a second (d-tap) and third (triple tap) on the same base as the player's
     * immunity runs out, the butterfly's obsidian stacked on top when the player rose past it.
     *
     * @return true when the combo goes on
     */
    private boolean followUp(ServerPlayer target, Combo current) {
        int maxTaps = has(BotSkill.TRIPLE_TAP) ? 3 : has(BotSkill.DOUBLE_TAP) ? 2 : 1;
        boolean airborne = !target.onGround();
        if (current.taps >= maxTaps || !target.isAlive() || this.body.distanceTo(target) > 6.0D
                || (!airborne && current.kind != Kind.CRYSTAL && !chainWindow())) {
            this.combo = null;
            return false;
        }
        current.step = Step.WAIT;
        current.afterWait = Step.CRYSTAL;
        if (has(BotSkill.BUTTERFLY) && !current.stacked && airborne && target.getY() > current.base.getY() + 2.5D
                && canPlaceBlock(current.base.above()) && this.level.isEmptyBlock(current.base.above(2))) {
            current.base = current.base.above();
            current.stacked = true;
            current.afterWait = Step.OBSIDIAN;
        }
        current.focus = topFace(current.base);
        return true;
    }

    /** Waits for the player's damage immunity to run out, timed so the blast lands just after. */
    private int stepWait(ServerPlayer target, Combo current) {
        int stepsLeft = current.afterWait == Step.ANCHOR ? (current.shield != null ? 3 : 2)
                : current.afterWait == Step.OBSIDIAN ? 2 : 1;
        if (PracticeCombat.immunity(target) > 10 + stepsLeft * this.difficulty.step) {
            return 0;
        }
        current.step = current.afterWait;
        return continueCombo(target);
    }

    private int stepAnchor(ServerPlayer target, Combo current) {
        if (!canPlaceBlock(current.anchor)) {
            Option retry = bestAnchorOption(target, minimumDamage() * 0.5F);
            if (retry == null) {
                this.combo = null;
                return -1;
            }
            current.anchor = retry.pos();
            current.shield = has(BotSkill.SAFE_ANCHOR) ? shieldSpot(retry.pos()) : null;
        }
        current.focus = Vec3.atCenterOf(current.anchor);
        if (!aim(current.focus)) {
            return 0;
        }
        placeBlock(current.anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState(), new ItemStack(Items.RESPAWN_ANCHOR, 64));
        current.step = Step.CHARGE;
        current.wait = this.difficulty.step - 1;
        return 1;
    }

    private int stepCharge(Combo current) {
        BlockState state = this.level.getBlockState(current.anchor);
        if (!state.is(Blocks.RESPAWN_ANCHOR)) {
            this.combo = null;
            return -1;
        }
        if (!aim(current.focus)) {
            return 0;
        }
        hold(new ItemStack(Items.GLOWSTONE, 64));
        // Vanilla's own charging: the charge level, its sound and game event.
        RespawnAnchorBlock.charge(this.body, this.level, current.anchor, state);
        swingArm();
        current.step = current.shield != null && canPlaceBlock(current.shield) ? Step.SHIELD : Step.DETONATE;
        current.wait = this.difficulty.step - 1;
        return 1;
    }

    private int stepShield(Combo current) {
        if (!canPlaceBlock(current.shield)) {
            current.step = Step.DETONATE;
            return 0;
        }
        Vec3 point = Vec3.atCenterOf(current.shield);
        if (!aim(point)) {
            return 0;
        }
        placeBlock(current.shield, Blocks.GLOWSTONE.defaultBlockState(), new ItemStack(Items.GLOWSTONE, 64));
        current.step = Step.DETONATE;
        current.wait = this.difficulty.step - 1;
        return 1;
    }

    private int stepDetonate(ServerPlayer target, Combo current) {
        BlockState state = this.level.getBlockState(current.anchor);
        if (!state.is(Blocks.RESPAWN_ANCHOR) || state.getValue(RespawnAnchorBlock.CHARGE) <= 0) {
            this.combo = null;
            return -1;
        }
        boolean shielded = current.shield != null && this.level.getBlockState(current.shield).is(Blocks.GLOWSTONE);
        if (!shielded && !safeToDetonate(Vec3.atCenterOf(current.anchor), PracticeCombat.ANCHOR_POWER, target)) {
            this.combo = null;
            return -1;
        }
        if (!aim(current.focus)) {
            return 0;
        }
        // Clicked with anything but glowstone, a charged anchor explodes outside the Nether.
        hold(this.sword);
        swingArm();
        ((RespawnAnchorInvoker) Blocks.RESPAWN_ANCHOR).crystalTweaks$explode(state, this.level, current.anchor);
        current.blasts++;
        if (has(BotSkill.DOUBLE_ANCHOR) && current.blasts < 2 && target.isAlive() && this.body.distanceTo(target) <= 6.0D) {
            current.step = Step.WAIT;
            current.afterWait = Step.ANCHOR;
            current.shield = has(BotSkill.SAFE_ANCHOR) ? shieldSpot(current.anchor) : null;
        } else {
            this.combo = null;
        }
        return 1;
    }

    // ------------------------------------------------------------------------------------------
    // Choosing spots
    // ------------------------------------------------------------------------------------------

    /**
     * Where the player's feet will be when a blast placed now goes off. Only the best bots read the
     * player's movement ahead; Hard ones do for their own knockback, which they know.
     */
    private Vec3 targetFeet(ServerPlayer target, int ticksAhead) {
        boolean ownKnock = this.age - this.knockedAt <= 15;
        if (!has(BotSkill.PREDICTION) && !(ownKnock && has(BotSkill.DOUBLE_TAP))) {
            return target.position();
        }
        double ground = groundBelow(target.position());
        if (ownKnock) {
            return PracticeCombat.predict(this.knockOrigin, this.knockVelocity, this.age - this.knockedAt + ticksAhead,
                    ground);
        }
        return PracticeCombat.predict(target.position(), this.targetVelocity, ticksAhead, ground);
    }

    private double groundBelow(Vec3 position) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(position).mutable();
        for (int depth = 0; depth < 8; depth++) {
            BlockPos below = cursor.below();
            if (!this.level.getBlockState(below).getCollisionShape(this.level, below).isEmpty()) {
                return cursor.getY();
            }
            cursor.move(Direction.DOWN);
        }
        return position.y - 8.0D;
    }

    /**
     * The best crystal: on obsidian or bedrock already there, or, if allowed, on obsidian it places
     * first beside the player's feet.
     */
    private Option bestCrystalOption(ServerPlayer target, float minimum, boolean allowObsidian) {
        int ahead = allowObsidian ? 2 * this.difficulty.step : this.difficulty.step;
        Vec3 feet = targetFeet(target, ahead);
        BlockPos feetBlock = BlockPos.containing(feet);
        List<BlockPos> bases = new ArrayList<>();
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -SEARCH; dx <= SEARCH; dx++) {
            for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                for (int dy = -3; dy <= 1; dy++) {
                    BlockPos position = feetBlock.offset(dx, dy, dz);
                    BlockState state = this.level.getBlockState(position);
                    if (crystalBase(state)) {
                        if (this.level.isEmptyBlock(position.above()) && inReach(topFace(position))) {
                            bases.add(position.immutable());
                        }
                    } else if (allowObsidian && Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && dy >= -1 && dy <= 0
                            && canPlaceBlock(position) && this.level.isEmptyBlock(position.above())
                            && inReach(Vec3.atCenterOf(position))) {
                        spots.add(position.immutable());
                    }
                }
            }
        }
        List<Option> options = new ArrayList<>();
        rank(bases, feet, 1.0D, 0.0D);
        rank(spots, feet, 1.0D, 0.0D);
        int evaluated = 0;
        for (BlockPos base : bases) {
            if (evaluated++ >= CANDIDATES) {
                break;
            }
            if (!crystalSpaceFree(base) || !canSee(topFace(base), base)) {
                continue;
            }
            Option option = crystalOption(target, base, feet, false, minimum);
            if (option != null) {
                options.add(option);
            }
        }
        evaluated = 0;
        for (BlockPos spot : spots) {
            if (evaluated++ >= CANDIDATES / 2) {
                break;
            }
            if (!crystalSpaceFree(spot) || !canSee(Vec3.atCenterOf(spot), null)) {
                continue;
            }
            Option option = crystalOption(target, spot, feet, true, minimum);
            if (option != null) {
                options.add(option);
            }
        }
        return pick(options);
    }

    private Option crystalOption(ServerPlayer target, BlockPos base, Vec3 feet, boolean needsObsidian, float minimum) {
        Vec3 center = topFace(base);
        float toTarget = PracticeCombat.explosionDamage(target, center, PracticeCombat.CRYSTAL_POWER, feet, this.settings);
        if (toTarget < minimum) {
            return null;
        }
        float toSelf = PracticeCombat.explosionDamage(this.body, center, PracticeCombat.CRYSTAL_POWER,
                this.body.position(), this.settings);
        if (!acceptable(target, toTarget, toSelf)) {
            return null;
        }
        return new Option(base, needsObsidian, false, toTarget, toSelf, score(target, toTarget, toSelf)
                - (needsObsidian ? 0.5F : 0.0F));
    }

    /** The best respawn anchor spot next to the player, on the head of one in a hole included. */
    private Option bestAnchorOption(ServerPlayer target, float minimum) {
        Vec3 feet = targetFeet(target, 2 * this.difficulty.step);
        BlockPos feetBlock = BlockPos.containing(feet);
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos spot = feetBlock.offset(dx, dy, dz);
                    if (canPlaceBlock(spot) && inReach(Vec3.atCenterOf(spot))) {
                        spots.add(spot.immutable());
                    }
                }
            }
        }
        rank(spots, feet, 1.0D, 0.5D);
        List<Option> options = new ArrayList<>();
        int evaluated = 0;
        for (BlockPos spot : spots) {
            if (evaluated++ >= CANDIDATES / 2) {
                break;
            }
            if (!canSee(Vec3.atCenterOf(spot), null)) {
                continue;
            }
            Vec3 center = Vec3.atCenterOf(spot);
            float toTarget = PracticeCombat.explosionDamage(target, center, PracticeCombat.ANCHOR_POWER, feet, this.settings);
            if (toTarget < minimum) {
                continue;
            }
            float toSelf = PracticeCombat.explosionDamage(this.body, center, PracticeCombat.ANCHOR_POWER,
                    this.body.position(), this.settings);
            if (has(BotSkill.SAFE_ANCHOR) && shieldSpot(spot) != null) {
                // Glowstone between it and the anchor takes most of the blast off it.
                toSelf *= 0.25F;
            }
            if (!acceptable(target, toTarget, toSelf)) {
                continue;
            }
            // An anchor takes a step more than a crystal on existing obsidian.
            options.add(new Option(spot, false, true, toTarget, toSelf, score(target, toTarget, toSelf) - 0.75F));
        }
        return pick(options);
    }

    private boolean acceptable(ServerPlayer target, float toTarget, float toSelf) {
        float health = PracticeCombat.effectiveHealth(this.body);
        boolean lethal = toTarget >= PracticeCombat.effectiveHealth(target) && !PracticeCombat.holdsTotem(target);
        if (toSelf >= health - 1.0F && !(lethal && PracticeCombat.holdsTotem(this.body) && has(BotSkill.CHAIN_POP))) {
            return false;
        }
        // A rusher with a totem in hand accepts a slightly losing trade; the rest never do.
        float ratio = this.aggressive && PracticeCombat.holdsTotem(this.body) ? 1.2F
                : has(BotSkill.CHAIN_POP) ? 1.0F : 0.8F;
        return lethal || toSelf <= toTarget * ratio;
    }

    private float score(ServerPlayer target, float toTarget, float toSelf) {
        boolean lethal = toTarget >= PracticeCombat.effectiveHealth(target) && !PracticeCombat.holdsTotem(target);
        return toTarget - toSelf * 0.6F + (lethal ? 20.0F : 0.0F);
    }

    /** The best option, or now and then the second or third for a less precise bot. */
    private Option pick(List<Option> options) {
        if (options.isEmpty()) {
            return null;
        }
        options.sort(Comparator.comparingDouble(option -> -option.score()));
        int index = 0;
        if (options.size() > 1 && this.random.nextFloat() > this.difficulty.precision) {
            index = 1 + this.random.nextInt(Math.min(2, options.size() - 1));
        }
        return options.get(index);
    }

    /** Cheap order first, so only the handful of spots that can matter are ray-traced. */
    private static void rank(List<BlockPos> positions, Vec3 feet, double horizontal, double lift) {
        positions.sort(Comparator.comparingDouble(position -> {
            double dx = position.getX() + 0.5D - feet.x;
            double dy = position.getY() + 1.0D - lift - feet.y;
            double dz = position.getZ() + 0.5D - feet.z;
            return (dx * dx + dz * dz) * horizontal + dy * dy;
        }));
    }

    /** A free block beside the anchor, on the bot's side, where glowstone would shield it. */
    private BlockPos shieldSpot(BlockPos anchor) {
        Vec3 center = Vec3.atCenterOf(anchor);
        Vec3 toSelf = this.body.getEyePosition().subtract(center);
        if (toSelf.lengthSqr() < 1.0E-4D) {
            return null;
        }
        Vec3 direction = toSelf.normalize();
        BlockPos best = null;
        double bestDot = 0.35D;
        for (Direction side : Direction.values()) {
            if (side == Direction.DOWN) {
                continue;
            }
            double dot = direction.x * side.getStepX() + direction.y * side.getStepY() + direction.z * side.getStepZ();
            BlockPos spot = anchor.relative(side);
            if (dot > bestDot && canPlaceBlockBeside(spot, anchor)) {
                bestDot = dot;
                best = spot;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------
    // Other decisions
    // ------------------------------------------------------------------------------------------

    /** Breaks any crystal in reach, whoever placed it, that hurts the player more than itself. */
    private boolean snipeCrystal(ServerPlayer target) {
        EndCrystal best = null;
        float bestScore = 0.0F;
        float minimum = Math.max(1.0F, minimumDamage() * 0.6F);
        for (EndCrystal crystal : this.level.getEntitiesOfClass(EndCrystal.class,
                this.body.getBoundingBox().inflate(HIT_REACH + 1.0D))) {
            if (crystal.isRemoved() || !inBreakReach(crystal)) {
                continue;
            }
            float toTarget = PracticeCombat.explosionDamage(target, crystal.position(), PracticeCombat.CRYSTAL_POWER,
                    target.position(), this.settings);
            if (toTarget < minimum) {
                continue;
            }
            float toSelf = PracticeCombat.explosionDamage(this.body, crystal.position(), PracticeCombat.CRYSTAL_POWER,
                    this.body.position(), this.settings);
            if (!acceptable(target, toTarget, toSelf)) {
                continue;
            }
            float score = score(target, toTarget, toSelf);
            if (score > bestScore) {
                bestScore = score;
                best = crystal;
            }
        }
        if (best == null) {
            return false;
        }
        Vec3 point = best.position().add(0.0D, 0.5D, 0.0D);
        if (!aim(point)) {
            return false;
        }
        hit(best);
        if (this.combo != null && this.combo.crystal == best) {
            this.combo.taps++;
            followUp(target, this.combo);
        }
        return true;
    }

    /** Covering its own flank, mending, or getting out. */
    private boolean defend(ServerPlayer target) {
        return blockOff(target) || topBlock(target) || mend(target) || escape(target);
    }

    /**
     * Puts a block in front of itself, between the player and it, where there is ground to set it
     * on: the blasts of crystals and anchors on the player's side hit the block instead. Only when
     * threatened, and it then stays behind it for a second instead of strafing out.
     */
    private boolean blockOff(ServerPlayer target) {
        if (!has(BotSkill.BLOCK_OFF) || this.blockOffCooldown > 0) {
            return false;
        }
        double distance = this.body.distanceTo(target);
        if (distance < 1.5D || distance > 6.0D || !threatened(target)) {
            return false;
        }
        Vec3 to = target.position().subtract(this.body.position());
        Direction side = Math.abs(to.x) > Math.abs(to.z)
                ? (to.x > 0.0D ? Direction.EAST : Direction.WEST)
                : (to.z > 0.0D ? Direction.SOUTH : Direction.NORTH);
        BlockPos front = this.body.blockPosition().relative(side);
        if (!solid(front.below()) || !canPlaceBlock(front)) {
            return false;
        }
        Vec3 point = Vec3.atCenterOf(front);
        if (!aim(point)) {
            return false;
        }
        placeBlock(front, Blocks.OBSIDIAN.defaultBlockState(), new ItemStack(Items.OBSIDIAN, 64));
        this.holdTicks = 20;
        this.blockOffCooldown = 30 + this.difficulty.reaction * 2;
        return true;
    }

    /** The player is about to crystal or anchor it: holding the tools for it, or it is getting low. */
    private boolean threatened(ServerPlayer target) {
        ItemStack held = target.getMainHandItem();
        return held.is(Items.END_CRYSTAL) || held.is(Items.OBSIDIAN) || held.is(Items.RESPAWN_ANCHOR)
                || held.is(Items.GLOWSTONE) || PracticeCombat.effectiveHealth(this.body) < 14.0F;
    }

    private boolean solid(BlockPos position) {
        return !this.level.getBlockState(position).getCollisionShape(this.level, position).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // Mining its way out
    // ------------------------------------------------------------------------------------------

    /**
     * Boxed in, or walking into a wall it cannot jump: it breaks the block in the way with its
     * pickaxe, at Vanilla's mining speed for that pickaxe and block, with the cracks showing.
     */
    private boolean mineOut(ServerPlayer target) {
        if (!has(BotSkill.MINE_OUT)) {
            return false;
        }
        if (this.miningPos != null) {
            return continueMining();
        }
        if (this.stuckTicks < 20 && !enclosed()) {
            return false;
        }
        BlockPos block = blockInTheWay(target);
        if (block == null) {
            this.stuckTicks = 0;
            return false;
        }
        this.miningPos = block;
        this.miningProgress = 0.0F;
        hold(PracticeKit.pickaxeFor(this.level, this.settings));
        return continueMining();
    }

    private boolean continueMining() {
        BlockPos position = this.miningPos;
        BlockState state = this.level.getBlockState(position);
        if (!minable(state, position) || this.body.getEyePosition().distanceToSqr(Vec3.atCenterOf(position)) > 25.0D) {
            stopMining();
            return false;
        }
        this.miningProgress += miningRate(state, position);
        if (this.age % 4 == 0) {
            swingArm();
        }
        if (this.miningProgress >= 1.0F) {
            this.level.destroyBlockProgress(this.body.getId(), position, -1);
            this.level.destroyBlock(position, false, this.body, 512);
            stopMining();
            this.stuckTicks = 0;
        } else {
            this.level.destroyBlockProgress(this.body.getId(), position, (int) (this.miningProgress * 10.0F) - 1);
        }
        return true;
    }

    private void stopMining() {
        if (this.miningPos != null) {
            this.level.destroyBlockProgress(this.body.getId(), this.miningPos, -1);
        }
        this.miningPos = null;
        this.miningProgress = 0.0F;
    }

    /** No way to walk or jump out in any direction. */
    private boolean enclosed() {
        BlockPos feet = this.body.blockPosition();
        boolean ceiling = solid(feet.above(2));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos step = feet.relative(direction);
            if (!solid(step) && !solid(step.above())) {
                return false;
            }
            if (solid(step) && !ceiling && !solid(step.above()) && !solid(step.above(2))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The block to break: toward the player first, then the other sides. At head height when that
     * is what blocks the way, otherwise at the feet.
     */
    private BlockPos blockInTheWay(ServerPlayer target) {
        BlockPos feet = this.body.blockPosition();
        Vec3 to = target.position().subtract(this.body.position());
        List<Direction> order = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        order.sort(Comparator.comparingDouble(direction -> -(direction.getStepX() * to.x + direction.getStepZ() * to.z)));
        boolean ceiling = solid(feet.above(2));
        for (Direction direction : order) {
            BlockPos head = feet.relative(direction).above();
            BlockPos low = feet.relative(direction);
            if (solid(head) && minable(this.level.getBlockState(head), head)) {
                return head;
            }
            if (solid(low) && (ceiling || solid(low.above(2))) && minable(this.level.getBlockState(low), low)) {
                return low;
            }
        }
        return null;
    }

    private boolean minable(BlockState state, BlockPos position) {
        return !state.isAir() && !state.is(Blocks.BEDROCK) && state.getFluidState().isEmpty()
                && state.getDestroySpeed(this.level, position) >= 0.0F;
    }

    /**
     * Vanilla's mining progress per tick: tool speed over hardness, divided by 30 with the right
     * tool and 100 without. Efficiency V adds 26 to a pickaxe's speed; in the air it is five times
     * slower.
     */
    private float miningRate(BlockState state, BlockPos position) {
        float hardness = state.getDestroySpeed(this.level, position);
        if (hardness <= 0.0F) {
            return 1.0F;
        }
        boolean pickaxe = state.is(BlockTags.MINEABLE_WITH_PICKAXE);
        float speed = pickaxe ? PracticeKit.pickaxeSpeed(this.settings) + 26.0F : 1.0F;
        boolean tierTooLow = state.is(BlockTags.NEEDS_DIAMOND_TOOL) && this.settings.armor == PracticeSettings.Armor.IRON;
        boolean correct = pickaxe ? !tierTooLow : !state.requiresCorrectToolForDrops();
        float rate = speed / hardness / (correct ? 30.0F : 100.0F);
        return this.body.onGround() ? rate : rate / 5.0F;
    }

    // ------------------------------------------------------------------------------------------
    // Ender pearls, aimed by simulating their flight
    // ------------------------------------------------------------------------------------------

    /**
     * The smart bot's retreat: low, with apples left and the player close, it does not back away
     * hopping. It pearls away to a spot it has checked it will land on, and eats there.
     */
    private boolean retreatWithPearl(ServerPlayer target) {
        if (!has(BotSkill.PEARL_ESCAPE) || this.eating || this.pearls <= 0 || this.pearlCooldown > 0 || this.apples <= 0
                || PracticeCombat.effectiveHealth(this.body) >= 10.0F || this.body.distanceTo(target) > 5.5D) {
            return false;
        }
        return pearlAway(target);
    }

    /**
     * Caps obsidian next to itself with glowstone, so the player cannot put a crystal there.
     */
    private boolean topBlock(ServerPlayer target) {
        if (!has(BotSkill.TOPBLOCK) || this.body.distanceTo(target) > 6.0D) {
            return false;
        }
        BlockPos feet = this.body.blockPosition();
        BlockPos best = null;
        float worst = 6.0F;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 0; dy++) {
                    BlockPos base = feet.offset(dx, dy, dz);
                    if (!crystalBase(this.level.getBlockState(base)) || !crystalSpaceFree(base)
                            || !inReach(topFace(base))) {
                        continue;
                    }
                    float threat = PracticeCombat.explosionDamage(this.body, topFace(base), PracticeCombat.CRYSTAL_POWER,
                            this.body.position(), this.settings);
                    if (threat > worst) {
                        worst = threat;
                        best = base;
                    }
                }
            }
        }
        if (best == null || !aim(topFace(best))) {
            return false;
        }
        placeBlock(best.above(), Blocks.GLOWSTONE.defaultBlockState(), new ItemStack(Items.GLOWSTONE, 64));
        return true;
    }

    /** Throws experience bottles at worn armour while the player is out of reach, as Mending needs. */
    private boolean mend(ServerPlayer target) {
        if (!has(BotSkill.MENDING) || this.mendCooldown > 0 || this.body.distanceTo(target) < 6.0D) {
            return false;
        }
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        float worst = 1.0F;
        for (EquipmentSlot slot : slots) {
            worst = Math.min(worst, PracticeCombat.durability(this.body.getItemBySlot(slot)));
        }
        if (worst > 0.55F) {
            return false;
        }
        hold(new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        swingArm();
        this.level.playSound(null, this.body.getX(), this.body.getY(), this.body.getZ(), SoundEvents.EXPERIENCE_BOTTLE_THROW,
                SoundSource.NEUTRAL, 0.5F, 0.4F / (this.random.nextFloat() * 0.4F + 0.8F));
        // Each bottle's experience, spent by Mending on the pieces worn: about a tenth of each.
        for (EquipmentSlot slot : slots) {
            ItemStack piece = this.body.getItemBySlot(slot);
            if (!piece.isEmpty() && piece.isDamageableItem()) {
                piece.setDamageValue(Math.max(0, piece.getDamageValue() - Math.max(1, piece.getMaxDamage() / 10)));
            }
        }
        this.mendCooldown = 3;
        return true;
    }

    /** Out of totems and nearly dead: an ender pearl away from the player. */
    private boolean escape(ServerPlayer target) {
        if (!has(BotSkill.PEARL_ESCAPE) || this.pearls <= 0 || this.pearlCooldown > 0 || this.totems > 0
                || PracticeCombat.holdsTotem(this.body) || PracticeCombat.effectiveHealth(this.body) > 6.0F) {
            return false;
        }
        return pearlAway(target);
    }

    /**
     * Pearls away from the player: straight back first, then turning up to ninety degrees either
     * way, to the first direction with a landing it has checked.
     */
    private boolean pearlAway(ServerPlayer target) {
        Vec3 away = this.body.position().subtract(target.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 1.0E-3D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize();
        double limit = PracticeArena.RADIUS - 6;
        for (double turn : new double[] {0.0D, 35.0D, -35.0D, 70.0D, -70.0D, 100.0D, -100.0D}) {
            double radians = Math.toRadians(turn);
            Vec3 direction = new Vec3(away.x * Math.cos(radians) - away.z * Math.sin(radians), 0.0D,
                    away.x * Math.sin(radians) + away.z * Math.cos(radians));
            Vec3 destination = this.body.position().add(direction.scale(12.0D));
            destination = new Vec3(Mth.clamp(destination.x, -limit, limit), destination.y, Mth.clamp(destination.z, -limit, limit));
            if (throwPearl(destination, 5.0D, target.position())) {
                return true;
            }
        }
        return false;
    }

    /** Far from the player: an ender pearl to land just in front of them. */
    private boolean chase(ServerPlayer target) {
        double range = this.aggressive ? 8.0D : 14.0D;
        if (!has(BotSkill.PEARL_CHASE) || this.pearls <= 0 || this.pearlCooldown > 0
                || this.body.distanceTo(target) < range) {
            return false;
        }
        Vec3 back = this.body.position().subtract(target.position());
        back = new Vec3(back.x, 0.0D, back.z);
        Vec3 destination = back.lengthSqr() > 1.0E-3D ? target.position().add(back.normalize().scale(2.5D)) : target.position();
        return throwPearl(destination, 3.5D, null);
    }

    /** A throw it has worked out: where to look, and where it will land. */
    private record PearlShot(float yaw, float pitch, Vec3 landing, double error) {
    }

    /**
     * Throws a pearl only when the flight it simulated lands within {@code tolerance} of the
     * destination on safe ground; otherwise keeps the pearl.
     *
     * @param avoid a point the landing must stay six blocks from, or {@code null}
     */
    private boolean throwPearl(Vec3 destination, double tolerance, Vec3 avoid) {
        if (this.pearlInFlight != null && !this.pearlInFlight.isRemoved()) {
            return false;
        }
        PearlShot shot = aimPearl(destination, tolerance, avoid);
        if (shot == null) {
            return false;
        }
        this.body.setYRot(shot.yaw());
        this.body.setYHeadRot(shot.yaw());
        this.body.setXRot(shot.pitch());
        hold(new ItemStack(Items.ENDER_PEARL, 16));
        ThrownEnderpearl pearl = new ThrownEnderpearl(this.level, this.body, new ItemStack(Items.ENDER_PEARL));
        pearl.shootFromRotation(this.body, shot.pitch(), shot.yaw(), 0.0F, 1.5F, 1.0F);
        this.level.addFreshEntity(pearl);
        this.pearlInFlight = pearl;
        this.level.playSound(null, this.body.getX(), this.body.getY(), this.body.getZ(), SoundEvents.ENDER_PEARL_THROW,
                SoundSource.NEUTRAL, 0.5F, 0.4F / (this.random.nextFloat() * 0.4F + 0.8F));
        swingArm();
        this.pearls--;
        this.pearlCooldown = 20;
        return true;
    }

    /**
     * Tries pitches from steep to flat, then small turns left and right, simulating each flight,
     * and keeps the one landing closest to the destination. About a thousand short ray casts for a
     * straight throw: well under a millisecond.
     */
    private PearlShot aimPearl(Vec3 destination, double tolerance, Vec3 avoid) {
        Vec3 start = new Vec3(this.body.getX(), this.body.getEyeY() - 0.1D, this.body.getZ());
        Vec3 moving = this.body.getDeltaMovement();
        // The thrower's own movement is added to the pearl's, as shootFromRotation does.
        Vec3 carried = new Vec3(moving.x, this.body.onGround() ? 0.0D : moving.y, moving.z);
        float baseYaw = (float) (Mth.atan2(destination.z - start.z, destination.x - start.x) * Mth.RAD_TO_DEG) - 90.0F;
        PearlShot best = null;
        for (float offset : new float[] {0.0F, -6.0F, 6.0F, -12.0F, 12.0F}) {
            float yaw = baseYaw + offset;
            for (float pitch = -65.0F; pitch <= 25.0F; pitch += 2.5F) {
                Vec3 landing = simulatePearl(start, pearlVelocity(yaw, pitch).add(carried));
                Vec3 ground = landing == null ? null : safeGround(landing);
                if (ground == null || (avoid != null && ground.distanceToSqr(avoid) < 36.0D)) {
                    continue;
                }
                double dx = ground.x - destination.x;
                double dz = ground.z - destination.z;
                double error = Math.sqrt(dx * dx + dz * dz);
                if (best == null || error < best.error()) {
                    best = new PearlShot(yaw, pitch, ground, error);
                }
            }
            if (best != null && best.error() <= tolerance * 0.5D) {
                break;
            }
        }
        return best != null && best.error() <= tolerance ? best : null;
    }

    /** The direction shootFromRotation gives a pitch and yaw, at a pearl's speed. */
    private static Vec3 pearlVelocity(float yaw, float pitch) {
        double x = -Mth.sin(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
        double y = -Mth.sin(pitch * Mth.DEG_TO_RAD);
        double z = Mth.cos(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
        return new Vec3(x, y, z).normalize().scale(1.5D);
    }

    /**
     * A pearl's flight, tick by tick as ThrowableProjectile moves it: gravity 0.03, then drag 0.99,
     * then the move, stopped by the first block in its path. The thrower lands where the pearl was
     * at the start of the tick it hit, which is what this returns; {@code null} for a pearl that
     * hits at once, sets off a crystal on the way or never lands.
     */
    private Vec3 simulatePearl(Vec3 start, Vec3 velocity) {
        Vec3 position = start;
        Vec3 motion = velocity;
        for (int tick = 0; tick < 100; tick++) {
            motion = new Vec3(motion.x, motion.y - 0.03D, motion.z).scale(0.99D);
            Vec3 next = position.add(motion);
            BlockHitResult hit = this.level.clip(new ClipContext(position, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS) {
                return tick == 0 ? null : position;
            }
            if (crossesCrystal(position, next)) {
                return null;
            }
            position = next;
            if (position.y < this.level.getMinY()) {
                return null;
            }
        }
        return null;
    }

    /** A pearl touching a crystal sets it off, so a path through one is ruled out. */
    private boolean crossesCrystal(Vec3 from, Vec3 to) {
        for (EndCrystal crystal : this.level.getEntitiesOfClass(EndCrystal.class, new AABB(from, to).inflate(0.5D))) {
            if (crystal.getBoundingBox().inflate(0.3D).clip(from, to).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where it ends up after landing: the ground under the landing point, within a four-block drop,
     * with room to stand, nothing that burns or pricks, and inside the arena.
     */
    private Vec3 safeGround(Vec3 landing) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(landing).mutable();
        double limit = PracticeArena.RADIUS - 4;
        for (int depth = 0; depth < 5; depth++) {
            BlockPos below = cursor.below();
            BlockState floor = this.level.getBlockState(below);
            if (!floor.getCollisionShape(this.level, below).isEmpty()) {
                if (solid(cursor) || solid(cursor.above()) || hurts(this.level.getBlockState(cursor)) || hurts(floor)
                        || Math.abs(landing.x) > limit || Math.abs(landing.z) > limit) {
                    return null;
                }
                return new Vec3(landing.x, cursor.getY(), landing.z);
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    private static boolean hurts(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK);
    }

    /**
     * A sword hit, a player's: full damage only when recharged, a critical one when falling, and the
     * sword's Knockback plus a sprint's on top of the hit's own.
     */
    private boolean melee(ServerPlayer target) {
        if (this.meleeCooldown > 0 || !inMeleeReach(target) || PracticeCombat.immunity(target) > 10) {
            return false;
        }
        Vec3 point = target.getEyePosition();
        if (!aim(point) || !canSee(point, null)) {
            return false;
        }
        hold(this.sword);
        boolean crit = has(BotSkill.CRITS) && !this.body.onGround() && this.body.getDeltaMovement().y < 0.0D;
        float base = switch (this.settings.armor) {
            case NETHERITE -> 8.0F;
            case DIAMOND -> 7.0F;
            case IRON -> 6.0F;
        };
        // Sharpness V adds three; a critical hit multiplies the base damage by one and a half.
        float damage = (crit ? base * 1.5F : base) + 3.0F;
        boolean sprinting = this.body.isSprinting();
        swingArm();
        this.meleeCooldown = MELEE_RECHARGE;
        boolean landed = target.hurtServer(this.level, this.level.damageSources().mobAttack(this.body), damage);
        if (!landed) {
            return true;
        }
        if (crit) {
            this.level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            this.level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5D), target.getZ(), 12, 0.3D, 0.4D,
                    0.3D, 0.2D);
        } else {
            this.level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    sprinting ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS,
                    1.0F, 1.0F);
        }
        // The hit's own knockback was applied by hurtServer; the sword's Knockback and the sprint
        // come on top, along the bot's facing, exactly as a player's attack adds them.
        double extra = this.settings.knockback * 0.5D + (sprinting ? 0.5D : 0.0D);
        float yaw = this.body.getYRot() * Mth.DEG_TO_RAD;
        Vec3 motion = PracticeCombat.knockback(target, extra, Mth.sin(yaw), -Mth.cos(yaw));
        target.setDeltaMovement(motion);
        // Sent now, as a player's hit is: left to the entity tracker, the next movement packet from
        // the ground would zero the upward part first. Cleared so the tracker does not send it twice.
        target.hurtMarked = false;
        target.connection.send(new ClientboundSetEntityMotionPacket(target));
        this.knockedAt = this.age;
        this.knockOrigin = target.position();
        this.knockVelocity = motion;
        if (sprinting) {
            // W-tap: the sprint is spent on the hit and has to be restarted for the next one.
            this.body.setSprinting(false);
            this.sprintReset = 4;
        }
        this.body.setDeltaMovement(this.body.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Actions, each what a player's click does on the server.
    // ------------------------------------------------------------------------------------------

    private void hit(EndCrystal crystal) {
        hold(new ItemStack(Items.END_CRYSTAL, 64));
        swingArm();
        // The same call a player's hit reaches on the server; the explosion is credited to the bot.
        crystal.hurtServer(this.level, this.level.damageSources().mobAttack(this.body), 1.0F);
    }

    /** Places a crystal the way Vanilla's crystal item does on the server. */
    private EndCrystal placeCrystal(BlockPos base) {
        if (!crystalBase(this.level.getBlockState(base)) || !crystalSpaceFree(base)) {
            return null;
        }
        BlockPos above = base.above();
        hold(new ItemStack(Items.END_CRYSTAL, 64));
        EndCrystal crystal = new EndCrystal(this.level, above.getX() + 0.5D, above.getY(), above.getZ() + 0.5D);
        crystal.setShowBottom(false);
        this.level.addFreshEntity(crystal);
        swingArm();
        return crystal;
    }

    private void placeBlock(BlockPos position, BlockState state, ItemStack held) {
        hold(held);
        this.level.setBlock(position, state, 3);
        this.level.playSound(null, position, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        swingArm();
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

    // ------------------------------------------------------------------------------------------
    // Checks
    // ------------------------------------------------------------------------------------------

    private static boolean crystalBase(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    private static Vec3 topFace(BlockPos base) {
        return new Vec3(base.getX() + 0.5D, base.getY() + 1.0D, base.getZ() + 0.5D);
    }

    /** Vanilla's placement test: air above the base and no entity at all in the crystal's space. */
    private boolean crystalSpaceFree(BlockPos base) {
        BlockPos above = base.above();
        if (!this.level.isEmptyBlock(above)) {
            return false;
        }
        AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0D, above.getY() + 2.0D,
                above.getZ() + 1.0D);
        return this.level.getEntities((Entity) null, space).isEmpty();
    }

    /** Somewhere a block can go: replaceable, against a solid face, with nobody standing in it. */
    private boolean canPlaceBlock(BlockPos position) {
        return canPlaceBlockBeside(position, null);
    }

    private boolean canPlaceBlockBeside(BlockPos position, BlockPos support) {
        if (!this.level.getBlockState(position).canBeReplaced()) {
            return false;
        }
        if (!this.level.getEntities((Entity) null, new AABB(position), entity -> entity.blocksBuilding).isEmpty()) {
            return false;
        }
        if (support != null) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = position.relative(direction);
            if (!this.level.getBlockState(neighbour).getCollisionShape(this.level, neighbour).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean inReach(Vec3 point) {
        return this.body.getEyePosition().distanceToSqr(point) <= PLACE_REACH * PLACE_REACH;
    }

    private boolean inMeleeReach(LivingEntity target) {
        return target.getBoundingBox().distanceToSqr(this.body.getEyePosition()) <= HIT_REACH * HIT_REACH;
    }

    private boolean inBreakReach(EndCrystal crystal) {
        return crystal.getBoundingBox().distanceToSqr(this.body.getEyePosition()) <= HIT_REACH * HIT_REACH
                && canSee(crystal.position().add(0.0D, 0.5D, 0.0D), null);
    }

    /** A clear line from its eyes, or one that ends on the block it means to click. */
    private boolean canSee(Vec3 point, BlockPos expected) {
        BlockHitResult hit = this.level.clip(new ClipContext(this.body.getEyePosition(), point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        if (expected != null && hit.getBlockPos().equals(expected)) {
            return true;
        }
        return hit.getLocation().distanceToSqr(point) < 0.36D;
    }

    /** Blowing this up leaves the bot standing, or, for a kill, standing on its totem. */
    private boolean safeToDetonate(Vec3 center, float power, ServerPlayer target) {
        float toSelf = PracticeCombat.explosionDamage(this.body, center, power, this.body.position(), this.settings);
        float toTarget = PracticeCombat.explosionDamage(target, center, power, target.position(), this.settings);
        return acceptable(target, Math.max(toTarget, 0.001F), toSelf) || toSelf < 1.0F;
    }
}
