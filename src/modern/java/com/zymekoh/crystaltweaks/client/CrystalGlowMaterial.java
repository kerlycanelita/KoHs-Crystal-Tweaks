package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import java.lang.reflect.Method;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The additive material every glow, flash and ground spill is drawn with, for 26.2.
 *
 * <p>Up to 26.1.x this was vanilla's dragon-ray material as it stood: additive, tested against
 * depth and writing none. 26.2 gave the dragon rays the default depth state, which writes depth, so
 * each halo started hiding every light drawn after it: the spill on the ground behind a crystal and
 * the other crystals' glow showed through as dark discs, and overlapping halos cut into each other.
 * This is a copy of that pipeline, read from vanilla's own, with only the depth writes turned off.</p>
 */
final class CrystalGlowMaterial {
    private static RenderType glow;

    private CrystalGlowMaterial() {
    }

    static RenderType glow() {
        if (glow == null) {
            glow = create();
        }
        return glow;
    }

    private static RenderType create() {
        RenderPipeline source = RenderPipelines.DRAGON_RAYS;
        try {
            if (!source.getShaderDefines().isEmpty()) {
                throw new IllegalStateException("the dragon-ray pipeline now carries shader defines");
            }
            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(CrystalTweaksClient.MOD_ID, "pipeline/glow"))
                    .withVertexShader(source.getVertexShader())
                    .withFragmentShader(source.getFragmentShader())
                    .withPolygonMode(source.getPolygonMode())
                    .withCull(source.isCull())
                    .withColorTargetState(source.getColorTargetState())
                    .withVertexBinding(0, source.getVertexFormatBinding(0))
                    .withPrimitiveTopology(source.getPrimitiveTopology())
                    .withDepthStencilState(new DepthStencilState(source.getDepthStencilState().depthTest(), false));
            for (BindGroupLayout layout : source.getBindGroupLayouts()) {
                builder.withBindGroupLayout(layout);
            }
            RenderSetup setup = RenderSetup.builder(builder.build()).createRenderSetup();
            // RenderType's factory is package-private; the class itself is the public currency every
            // renderer passes around.
            Method factory = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
            factory.setAccessible(true);
            return (RenderType) factory.invoke(null, CrystalTweaksClient.MOD_ID + "_glow", setup);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            CrystalTweaksClient.LOGGER.warn(
                    "Could not build the glow material; falling back to the dragon rays, which write depth here",
                    exception);
            return RenderTypes.dragonRays();
        }
    }
}
