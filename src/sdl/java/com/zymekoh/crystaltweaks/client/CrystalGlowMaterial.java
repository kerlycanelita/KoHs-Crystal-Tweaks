package com.zymekoh.crystaltweaks.client;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.mixin.client.RenderTypeInvoker;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The additive materials the glow is drawn with, for 26.3.
 *
 * <p>As on 26.2, vanilla's dragon rays and energy swirl write depth here, so each light would hide
 * every light drawn after it. Both materials are copies of vanilla's pipelines with only the depth
 * writes turned off, keeping vanilla's order-independent transparency variants for when the player
 * has that enabled.</p>
 */
final class CrystalGlowMaterial {
    private static RenderType glow;
    private static RenderType layers;

    private CrystalGlowMaterial() {
    }

    static RenderType glow() {
        if (glow == null) {
            glow = create(RenderPipelines.DRAGON_RAYS, RenderPipelines.OIT_DRAGON_RAYS, "glow", null,
                    RenderTypes::dragonRays);
        }
        return glow;
    }

    /** Vanilla's energy swirl without its depth writes; see 26.1.x's copy of this class. */
    static RenderType layers(Identifier texture) {
        if (layers == null) {
            layers = create(RenderPipelines.ENERGY_SWIRL, RenderPipelines.OIT_ENERGY_SWIRL, "layer_glow", texture,
                    () -> RenderTypes.energySwirl(texture, 0.0F, 0.0F));
        }
        return layers;
    }

    /**
     * @param texture  the texture the copy samples, or {@code null} for an untextured one
     * @param fallback vanilla's own material, used if the copy cannot be built
     */
    private static RenderType create(RenderPipeline source, OitPipelineSet oit, String name, Identifier texture,
            Supplier<RenderType> fallback) {
        try {
            DepthStencilState depth = source.getDepthStencilState();
            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(CrystalTweaksClient.MOD_ID, "pipeline/" + name))
                    .withVertexShader(source.getShaders().get(ShaderType.VERTEX))
                    .withFragmentShader(source.getShaders().get(ShaderType.FRAGMENT))
                    .withPolygonMode(source.getPolygonMode())
                    .withCull(source.isCull())
                    .withVertexBinding(0, source.getVertexFormatBinding(0))
                    .withPrimitiveTopology(source.getPrimitiveTopology())
                    .withPushConstantSize(source.pushConstantSize())
                    .withDepthStencilState(new DepthStencilState(depth.depthTest(), false,
                            depth.depthBiasScaleFactor(), depth.depthBiasConstant()));
            copyDefines(builder, source.getShaderDefines());
            List<ColorTargetState> targets = source.getColorTargetStates();
            for (int index = 0; index < targets.size(); index++) {
                builder.withColorTargetState(index, targets.get(index));
            }
            for (BindGroupLayout layout : source.getBindGroupLayouts()) {
                builder.withBindGroupLayout(layout);
            }
            RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(builder.build()).setOitPipelines(oit);
            if (texture != null) {
                setup.withTexture("Sampler0", texture).useLightmap().useOverlay().sortOnUpload();
            }
            return RenderTypeInvoker.crystalTweaks$create(CrystalTweaksClient.MOD_ID + "_" + name,
                    setup.createRenderSetup());
        } catch (RuntimeException exception) {
            CrystalTweaksClient.LOGGER.warn(
                    "Could not build the {} material; falling back to vanilla's, which writes depth here",
                    name, exception);
            return fallback.get();
        }
    }

    /** The source's shader switches and values, the values read back the way they were written. */
    private static void copyDefines(RenderPipeline.Builder builder, ShaderDefines defines) {
        defines.flags().forEach(builder::withShaderDefine);
        defines.values().forEach((define, value) -> {
            try {
                builder.withShaderDefine(define, Integer.parseInt(value));
            } catch (NumberFormatException notInteger) {
                builder.withShaderDefine(define, Float.parseFloat(value));
            }
        });
    }
}
