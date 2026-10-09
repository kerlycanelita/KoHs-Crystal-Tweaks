package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.mixin.client.EntityTypeFactoryAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/**
 * Converter My Crystal: an item, a block or an entity drawn in a crystal's place.
 *
 * <p>Drawing only. The crystal stays the entity the server sent, with its hitbox, where it is; what
 * stands in for it floats, turns and is sized by the crystal's own settings.</p>
 */
public final class CrystalConverter {
    public static final String ITEM = "item:";
    public static final String ENTITY = "entity:";
    /** How much room an entity's larger side takes, in blocks. */
    private static final float ENTITY_SPAN = 1.0F;
    /** No entity the server sends has a negative id. */
    private static final int STAND_IN_ID = -0x4B6F48;

    /**
     * What a choice draws, worked out once.
     *
     * @param tumbles whether it turns on its corner as the crystal's frames do: blocks do, the rest
     *                stand upright
     */
    private record Shown(ItemStackRenderState item, boolean tumbles, EntityRenderState entity, float entityScale,
            float entityHeight) {
    }

    private static final Shown NOTHING = new Shown(null, false, null, 0.0F, 0.0F);
    private static final Map<String, Shown> SHOWN = new HashMap<>();
    private static Level shownIn;
    private static boolean listening;
    /** Scratch for the turn of the crystal being drawn; the render thread's alone. */
    private static final Matrix4f TURN = new Matrix4f();

    private CrystalConverter() {
    }

    /** The world is gone: so are the stand-ins built in it, which would keep it in memory. */
    public static void forget() {
        SHOWN.clear();
        shownIn = null;
    }

    /**
     * Whether the game can make item stacks yet. Their components are bound when the first world of
     * the session loads: on the title screen before that, asking for a stack throws.
     */
    public static boolean itemsReady() {
        try {
            return !new ItemStack(Items.END_CRYSTAL).isEmpty();
        } catch (RuntimeException unbound) {
            return false;
        }
    }

    /** Whether a choice can be drawn right now: an entity needs a world to exist in. */
    public static boolean drawable(String target) {
        return shown(target) != null;
    }

    /**
     * Draws the profile's choice where the crystal is.
     *
     * @return false when there is nothing to draw in its place, and the crystal is drawn as usual
     */
    public static boolean submit(CrystalAppearance look, EndCrystalRenderState state, PoseStack poses,
            SubmitNodeCollector collector, CameraRenderState camera) {
        if (!look.converted()) {
            return false;
        }
        Shown shown = shown(look.converterTarget);
        if (shown == null) {
            return false;
        }
        float size = CrystalPose.size(look);
        poses.pushPose();
        poses.translate(0.0F, CrystalPose.centreHeight(state.ageInTicks, look), 0.0F);
        if (shown.item != null) {
            // An item's model is one block wide, which is the width of the crystal's outer frame.
            // As a matrix: the one way to turn a pose that every supported version has.
            poses.mulPose(shown.tumbles ? TURN.rotation(CrystalPose.outerTurn(state.ageInTicks, look))
                    : TURN.rotationY(CrystalPose.turn(state.ageInTicks, look)));
            poses.scale(size, size, size);
            shown.item.submit(poses, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        } else {
            float scale = shown.entityScale * size;
            poses.mulPose(TURN.rotationY(CrystalPose.turn(state.ageInTicks, look)));
            poses.scale(scale, scale, scale);
            poses.translate(0.0F, -shown.entityHeight / 2.0F, 0.0F);
            // One state for every crystal of the profile: lit as this crystal is when it is submitted.
            shown.entity.lightCoords = state.lightCoords;
            shown.entity.ageInTicks = state.ageInTicks;
            submitEntity(Minecraft.getInstance().getEntityRenderDispatcher(), shown.entity, poses, collector, camera);
        }
        poses.popPose();
        return true;
    }

    private static <S extends EntityRenderState> void submitEntity(EntityRenderDispatcher dispatcher, S state, PoseStack poses,
            SubmitNodeCollector collector, CameraRenderState camera) {
        // The renderer itself, not the dispatcher: no shadow, fire or hitbox belongs to a stand-in.
        dispatcher.getRenderer(state).submit(state, poses, collector, camera);
    }

    private static Shown shown(String target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!listening && minecraft.getResourceManager() instanceof ReloadableResourceManager manager) {
            // A resource pack may bring other models: every choice is looked up again after a reload.
            manager.registerReloadListener((ResourceManagerReloadListener) ignored -> SHOWN.clear());
            listening = true;
        }
        if (minecraft.level != shownIn) {
            // An entity lives in one world, and a stand-in must not keep the last one alive.
            SHOWN.clear();
            shownIn = minecraft.level;
        }
        Shown shown = SHOWN.get(target);
        if (shown == null) {
            try {
                shown = resolve(minecraft, target);
            } catch (RuntimeException exception) {
                CrystalTweaksClient.LOGGER.warn("Converter My Crystal cannot draw {}; the crystal stays a crystal", target, exception);
                shown = NOTHING;
            }
            SHOWN.put(target, shown);
        }
        return shown == NOTHING ? null : shown;
    }

    /** Built by the type's own factory: a stand-in is drawn, never spawned, whatever the difficulty. */
    @SuppressWarnings("unchecked")
    private static <T extends Entity> T standIn(EntityType<T> type, Level level) {
        return ((EntityTypeFactoryAccessor<T>) (Object) type).crystalTweaks$factory().create(type, level);
    }

    private static Shown resolve(Minecraft minecraft, String target) {
        if (target.startsWith(ITEM)) {
            Identifier id = Identifier.tryParse(target.substring(ITEM.length()));
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
            if (item == Items.AIR || !itemsReady()) {
                return NOTHING;
            }
            ItemStackRenderState state = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(state, new ItemStack(item), ItemDisplayContext.NONE,
                    minecraft.level, null, 0);
            return state.isEmpty() ? NOTHING : new Shown(state, state.usesBlockLight(), null, 0.0F, 0.0F);
        }
        if (target.startsWith(ENTITY) && minecraft.level != null) {
            Identifier id = Identifier.tryParse(target.substring(ENTITY.length()));
            EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            Entity entity = type == null ? null : standIn(type, minecraft.level);
            if (entity == null) {
                return NOTHING;
            }
            // Never added to the world: from 26.2 an entity without an id cannot be asked for one,
            // and drawing a mob asks. It stands where the player is, in chunks that are loaded.
            entity.setId(STAND_IN_ID);
            if (minecraft.player != null) {
                entity.setPos(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
            }
            EntityRenderState state = minecraft.getEntityRenderDispatcher().extractEntity(entity, 1.0F);
            state.nameTag = null;
            float span = Math.max(0.25F, Math.max(entity.getBbWidth(), entity.getBbHeight()));
            return new Shown(null, false, state, ENTITY_SPAN / span, entity.getBbHeight());
        }
        return NOTHING;
    }
}
