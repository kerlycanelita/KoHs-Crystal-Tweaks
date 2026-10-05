package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.zymekoh.crystaltweaks.CrystalTweaksClient;
import com.zymekoh.crystaltweaks.mixin.client.RenderTypeInvoker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The additive materials the glow is drawn with, for 1.21.11.
 *
 * <p>The same materials as 26.1.x's copy of this class. 1.21.11 builds pipelines with blend
 * functions and depth flags of their own rather than 26.1's colour-target and depth-stencil states,
 * so the layer material is copied field by field here.</p>
 */
final class CrystalGlowMaterial {
    private static RenderType layers;

    private CrystalGlowMaterial() {
    }

    static RenderType glow() {
        return RenderTypes.dragonRays();
    }

    /** Vanilla's energy swirl without its depth writes; see 26.1.x's copy of this class. */
    static RenderType layers(Identifier texture) {
        if (layers == null) {
            layers = createLayers(texture);
        }
        return layers;
    }

    private static RenderType createLayers(Identifier texture) {
        RenderPipeline source = RenderPipelines.ENERGY_SWIRL;
        try {
            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(CrystalTweaksClient.MOD_ID, "pipeline/layer_glow"))
                    .withVertexShader(source.getVertexShader())
                    .withFragmentShader(source.getFragmentShader())
                    .withDepthTestFunction(source.getDepthTestFunction())
                    .withPolygonMode(source.getPolygonMode())
                    .withCull(source.isCull())
                    .withColorWrite(source.isWriteColor(), source.isWriteAlpha())
                    .withDepthWrite(false)
                    .withColorLogic(source.getColorLogic())
                    .withDepthBias(source.getDepthBiasScaleFactor(), source.getDepthBiasConstant())
                    .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode());
            source.getBlendFunction().ifPresentOrElse(builder::withBlend, builder::withoutBlend);
            copyDefines(builder, source.getShaderDefines());
            source.getSamplers().forEach(builder::withSampler);
            for (RenderPipeline.UniformDescription uniform : source.getUniforms()) {
                if (uniform.textureFormat() != null) {
                    builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
                } else {
                    builder.withUniform(uniform.name(), uniform.type());
                }
            }
            return RenderTypeInvoker.crystalTweaks$create(CrystalTweaksClient.MOD_ID + "_layer_glow",
                    RenderSetup.builder(builder.build()).withTexture("Sampler0", texture)
                            .useLightmap().useOverlay().sortOnUpload().createRenderSetup());
        } catch (RuntimeException exception) {
            CrystalTweaksClient.LOGGER.warn(
                    "Could not build the layer glow material; falling back to the energy swirl, which writes depth",
                    exception);
            return RenderTypes.energySwirl(texture, 0.0F, 0.0F);
        }
    }

    /** The source's shader switches and values, the values read back the way they were written. */
    private static void copyDefines(RenderPipeline.Builder builder, ShaderDefines defines) {
        defines.flags().forEach(builder::withShaderDefine);
        defines.values().forEach((name, value) -> {
            try {
                builder.withShaderDefine(name, Integer.parseInt(value));
            } catch (NumberFormatException notInteger) {
                builder.withShaderDefine(name, Float.parseFloat(value));
            }
        });
    }
}
