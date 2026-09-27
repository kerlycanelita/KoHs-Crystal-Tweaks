package com.zymekoh.crystaltweaks.client;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The additive material every glow, flash and ground spill is drawn with, for 26.3.
 *
 * <p>As on 26.2, vanilla's dragon rays write depth here, so each halo would hide every light drawn
 * after it. This copies that pipeline from vanilla's own with only the depth writes turned off, and
 * keeps vanilla's order-independent transparency variant for when the player has that enabled.</p>
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
            if (!source.getShaderDefines().isEmpty() || source.pushConstantSize() != 0) {
                throw new IllegalStateException("the dragon-ray pipeline changed shape");
            }
            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(CrystalTweaksClient.MOD_ID, "pipeline/glow"))
                    .withVertexShader(source.getShaders().get(ShaderType.VERTEX))
                    .withFragmentShader(source.getShaders().get(ShaderType.FRAGMENT))
                    .withPolygonMode(source.getPolygonMode())
                    .withCull(source.isCull())
                    .withVertexBinding(0, source.getVertexFormatBinding(0))
                    .withPrimitiveTopology(source.getPrimitiveTopology())
                    .withDepthStencilState(new DepthStencilState(source.getDepthStencilState().depthTest(), false));
            List<ColorTargetState> targets = source.getColorTargetStates();
            for (int index = 0; index < targets.size(); index++) {
                builder.withColorTargetState(index, targets.get(index));
            }
            for (BindGroupLayout layout : source.getBindGroupLayouts()) {
                builder.withBindGroupLayout(layout);
            }
            RenderSetup setup = RenderSetup.builder(builder.build())
                    .setOitPipelines(RenderPipelines.OIT_DRAGON_RAYS)
                    .createRenderSetup();
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
