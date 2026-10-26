package com.zymekoh.crystaltweaks.client.practice;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.mixin.client.GameRendererPracticeAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The live view of the server parked behind the practice world, for the window in the top-left
 * corner, drawn a few times a second.
 *
 * <p>Minecraft draws one world: its renderer keeps that world's chunks built on the graphics card and
 * its extractor works out each frame what to draw. The view has a renderer and an extractor of its
 * own on the parked server's world, so neither world rebuilds its chunks when the other one draws.
 * They take turns on the frame: before the practice world draws, the view draws the server's world
 * into the frame, from the parked player's eyes and in that world's own light and fog, copies the
 * picture into a texture of its own, and leaves the frame to the practice world, which clears it.</p>
 *
 * <p>Sodium and Iris replace Minecraft's world renderer with their own, which a second renderer
 * would not go through: with either installed the view stays off, and the window shows the rest.</p>
 *
 * <p>Render thread only.</p>
 */
public final class ServerView {
    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("crystal_tweaks", "practice/server_view");
    /** Fifteen pictures a second: enough to follow a fight, a fraction of the practice world's cost. */
    private static final long FRAME_NANOS = 1_000_000_000L / 15L;
    private static final Vector4f CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
    private static final boolean RENDERER_REPLACED = FabricLoader.getInstance().isModLoaded("sodium")
            || FabricLoader.getInstance().isModLoaded("iris")
            || FabricLoader.getInstance().isModLoaded("embeddium");

    private static ServerView current;
    /** The view failed once this session: it stays off rather than fail every frame. */
    private static boolean broken;

    private final LevelRenderer renderer;
    private final LevelExtractor extractor;
    private final Camera camera = new Camera();
    private final FogRenderer fog = new FogRenderer();
    private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("Crystal Practice server view");
    private final CrossFrameResourcePool resources = new CrossFrameResourcePool(3);
    /** Where the camera stands and the frame's size, which the terrain's shaders place chunks by. */
    private final GlobalSettingsUniform globals = new GlobalSettingsUniform();
    private final LightmapRenderState lightmap = new LightmapRenderState();
    private final RandomSource random = RandomSource.create();
    private float flicker;
    private final ViewTexture texture = new ViewTexture();
    private TextureTarget picture;
    private ClientLevel level;
    private long lastFrame;

    private ServerView(Minecraft minecraft) {
        GameRenderer gameRenderer = minecraft.gameRenderer;
        RenderTarget frame = gameRenderer.mainRenderTarget();
        this.renderer = new LevelRenderer(minecraft.getEntityRenderDispatcher(), minecraft.getBlockEntityRenderDispatcher(),
                minecraft.getModelManager(), minecraft.getTextureManager(), minecraft.getAtlasManager(), minecraft.getShaderManager(),
                gameRenderer, frame.width, frame.height);
        this.extractor = new LevelExtractor(minecraft, gameRenderer.gameRenderState().levelRenderState, this.renderer);
        minecraft.getTextureManager().register(TEXTURE, this.texture);
    }

    // ------------------------------------------------------------------------------------------
    // For the window and the settings
    // ------------------------------------------------------------------------------------------

    /** Why the live view cannot show at all, or null when it can. */
    static String unavailableReason(boolean spanish) {
        if (RENDERER_REPLACED) {
            return spanish
                    ? "Sodium o Iris cambian cómo Minecraft dibuja el mundo, y la vista en vivo no funciona con ellos: se queda apagada."
                    : "Sodium or Iris change how Minecraft draws the world, and the live view does not work with them: it stays off.";
        }
        if (broken) {
            return spanish
                    ? "La vista en vivo falló en esta sesión y se apagó; vuelve al reiniciar el juego."
                    : "The live view failed this session and turned off; it comes back when the game restarts.";
        }
        return null;
    }

    /** The texture holding the last picture, or null while there is none. */
    static Identifier texture() {
        return current != null && current.picture != null ? TEXTURE : null;
    }

    /** The picture's width over its height: the game window's. */
    static float aspect() {
        return current != null && current.picture != null && current.picture.height > 0
                ? current.picture.width / (float) current.picture.height
                : 16.0F / 9.0F;
    }

    private static boolean wanted() {
        return !RENDERER_REPLACED && !broken && CrystalVisualConfig.practiceServerWindow() && CrystalVisualConfig.practiceServerView();
    }

    // ------------------------------------------------------------------------------------------
    // Each frame and each of the parked server's ticks
    // ------------------------------------------------------------------------------------------

    /** Before the frame: a new picture of the parked server's world, when one is due. */
    static void render(Minecraft minecraft) {
        if (!wanted()) {
            close();
            return;
        }
        ClientLevel level = PracticeHost.serverLevel();
        if (level == null || PracticeHost.serverPlayer() == null) {
            return;
        }
        RenderTarget frame = minecraft.gameRenderer.mainRenderTarget();
        if (frame.width <= 0 || frame.height <= 0) {
            return;
        }
        long now = System.nanoTime();
        if (current != null && now - current.lastFrame < FRAME_NANOS) {
            return;
        }
        try {
            if (current == null) {
                current = new ServerView(minecraft);
            }
            ServerView view = current;
            view.lastFrame = now;
            PracticeHost.asServer(() -> view.draw(minecraft, level));
        } catch (RuntimeException failure) {
            CrystalTweaksClient.LOGGER.error("The live view of the server behind Crystal Practice failed; it stays off", failure);
            broken = true;
            close();
        }
    }

    /** One of the parked server's ticks: the view's camera and light keep up with it. */
    static void tick() {
        if (current != null && current.level != null && current.camera.entity() != null) {
            current.camera.tick();
            // The flicker of block light, as Minecraft's lightmap ticks it.
            current.flicker += (current.random.nextFloat() - current.random.nextFloat()) * current.random.nextFloat() * current.random.nextFloat() * 0.1F;
            current.flicker *= 0.9F;
        }
    }

    /**
     * Draws the parked server's world, with Minecraft's fields already switched to it, into the
     * frame, and keeps the picture.
     */
    private void draw(Minecraft minecraft, ClientLevel level) {
        if (this.level != level) {
            // A new world: the extractor starts over on it and is told about the chunks it already has.
            this.level = level;
            this.camera.setLevel(level);
            this.extractor.setLevel(level);
            PracticeGlue.announceChunks(level, minecraft.player.getBlockX() >> 4, minecraft.player.getBlockZ() >> 4);
        }
        Entity eye = minecraft.getCameraEntity();
        this.camera.setEntity(eye != null ? eye : minecraft.player);
        GameRenderer gameRenderer = minecraft.gameRenderer;
        RenderTarget frame = gameRenderer.mainRenderTarget();
        if (this.picture == null || this.picture.width != frame.width || this.picture.height != frame.height) {
            if (this.picture != null) {
                this.picture.destroyBuffers();
            }
            this.picture = new TextureTarget("Crystal Practice server view", frame.width, frame.height, false, GpuFormat.RGBA8_UNORM);
            this.texture.show(this.picture);
            this.renderer.resize(frame.width, frame.height);
        }

        DeltaTracker delta = minecraft.getDeltaTracker();
        this.camera.update(delta);
        float entityPartial = this.camera.getCameraEntityPartialTicks(delta);
        float worldPartial = delta.getGameTimeDeltaPartialTick(false);
        // The server's own light, its time of day and its dimension, in the lightmap the world draws
        // with; the practice world writes its own back before it draws.
        light(minecraft, level, entityPartial);
        ((GameRendererPracticeAccessor) gameRenderer).crystalTweaks$lightmap().render(this.lightmap);

        LevelRenderState state = gameRenderer.gameRenderState().levelRenderState;
        CameraRenderState cameraState = state.cameraRenderState;
        this.camera.extractRenderState(cameraState, entityPartial);
        cameraState.fogType = this.camera.getFluidInCamera();
        cameraState.fogData = this.fog.setupFog(this.camera, minecraft.options.getEffectiveRenderDistance(), delta, 0.0F, level);
        this.extractor.extract(delta, this.camera, worldPartial);
        // The particle engine holds the practice world's particles: none of them belong in this one.
        state.particlesRenderState.reset();

        GpuBufferSlice shownFog = RenderSystem.getShaderFog();
        GpuBufferSlice shownProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType shownProjectionType = RenderSystem.getProjectionType();
        try {
            this.globals.update(frame.width, frame.height, gameRenderer.gameRenderState().optionsRenderState.glintStrength,
                    level.getGameTime(), delta, 0, cameraState.pos,
                    gameRenderer.gameRenderState().optionsRenderState.textureFiltering == TextureFilteringMethod.RGSS);
            RenderSystem.setProjectionMatrix(this.projection.getBuffer(new Matrix4f(cameraState.projectionMatrix)), ProjectionType.PERSPECTIVE);
            this.fog.updateBuffer(cameraState.fogData);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(frame.getColorTexture(), CLEAR, frame.getDepthTexture(), 0.0);
            this.renderer.render(this.resources, delta, false, cameraState, cameraState.viewRotationMatrix,
                    this.fog.getBuffer(FogRenderer.FogMode.WORLD), cameraState.fogData.color, true);
            encoder.copyTextureToTexture(frame.getColorTexture(), this.picture.getColorTexture(), 0, 0, 0, 0, 0, frame.width, frame.height);
        } finally {
            RenderSystem.setShaderFog(shownFog);
            if (shownProjection != null) {
                RenderSystem.setProjectionMatrix(shownProjection, shownProjectionType);
            }
            this.fog.endFrame();
            this.resources.endFrame();
        }
    }

    /**
     * The lightmap of the parked server's world, worked out as Minecraft works out the one on show,
     * from the view's own camera: its environment, the time of day and the player's effects.
     */
    private void light(Minecraft minecraft, ClientLevel level, float partialTicks) {
        LightmapRenderState light = this.lightmap;
        EnvironmentAttributeProbe probe = this.camera.attributeProbe();
        LocalPlayer player = minecraft.player;
        light.needsUpdate = true;
        light.blockFactor = this.flicker + 1.4F;
        light.blockLightTint = ARGB.vector3fFromRGB24(probe.getValue(EnvironmentAttributes.BLOCK_LIGHT_TINT, partialTicks));
        light.skyFactor = probe.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, partialTicks);
        light.skyLightColor = ARGB.vector3fFromRGB24(probe.getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, partialTicks));
        EndFlashState endFlash = level.endFlashState();
        if (endFlash != null && !minecraft.options.hideLightningFlash().get()) {
            light.skyFactor += endFlash.getIntensity(partialTicks);
        }
        light.ambientColor = ARGB.vector3fFromRGB24(probe.getValue(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, partialTicks));
        float darknessOption = minecraft.options.darknessEffectScale().get().floatValue();
        float darkness = player.getEffectBlendFactor(MobEffects.DARKNESS, partialTicks) * darknessOption;
        light.brightness = Math.max(0.0F, minecraft.options.gamma().get().floatValue() - darkness);
        light.darknessEffectScale = Math.max(0.0F, Mth.cos((player.tickCount - partialTicks) * (float) Math.PI * 0.025F) * 0.45F * darkness)
                * darknessOption;
        float waterVision = player.getWaterVision();
        if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            light.nightVisionEffectIntensity = GameRenderer.nightVisionScale(player, partialTicks);
        } else if (waterVision > 0.0F && player.hasEffect(MobEffects.CONDUIT_POWER)) {
            light.nightVisionEffectIntensity = waterVision;
        } else {
            light.nightVisionEffectIntensity = 0.0F;
        }
        light.nightVisionColor = ARGB.vector3fFromRGB24(probe.getValue(EnvironmentAttributes.NIGHT_VISION_COLOR, partialTicks));
        light.bossOverlayWorldDarkening = 0.0F;
    }

    /** The parked server is gone, or the view was turned off: everything it holds is let go. */
    static void close() {
        ServerView view = current;
        if (view == null) {
            return;
        }
        current = null;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getTextureManager().release(TEXTURE);
        view.extractor.setLevel(null);
        view.renderer.close();
        view.fog.close();
        view.projection.close();
        view.resources.close();
        view.globals.close();
        if (view.picture != null) {
            view.picture.destroyBuffers();
        }
    }

    // ------------------------------------------------------------------------------------------
    // The parked server's block changes
    // ------------------------------------------------------------------------------------------

    /**
     * A world tells Minecraft's extractor when its blocks change. While the parked server's code
     * runs, those changes are its world's: they belong to the view's extractor, or to nobody when there
     * is no view, and never to the practice world's.
     */
    public static boolean divert(LevelExtractor target) {
        return PracticeHost.serverSide() && target == Minecraft.getInstance().levelExtractor;
    }

    /** Where a diverted change goes: the view's extractor, or null to drop it. */
    public static LevelExtractor extractor() {
        return current != null && current.level != null ? current.extractor : null;
    }

    /** The picture as a texture the window can draw; the picture keeps its own memory. */
    private static final class ViewTexture extends AbstractTexture {
        void show(RenderTarget target) {
            this.texture = target.getColorTexture();
            this.textureView = target.getColorTextureView();
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }

        @Override
        public void close() {
            this.texture = null;
            this.textureView = null;
        }
    }
}
