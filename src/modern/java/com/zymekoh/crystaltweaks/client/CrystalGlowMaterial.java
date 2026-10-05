package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.DepthStencilState;
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
 * The additive materials the glow is drawn with, for 26.2.
 *
 * <p>Up to 26.1.x the halo was vanilla's dragon-ray material as it stood: additive, tested against
 * depth and writing none. 26.2 gave the dragon rays the default depth state, which writes depth, so
 * each halo started hiding every light drawn after it: the spill on the ground behind a crystal and
 * the other crystals' glow showed through as dark discs, and overlapping halos cut into each other.
 * Both materials here are copies of vanilla pipelines, read from vanilla's own, with only the depth
 * writes turned off.</p>
 */
final class CrystalGlowMaterial {
    private static RenderType glow;
    private static RenderType layers;

    private CrystalGlowMaterial() {
    }

    static RenderType glow() {
        if (glow == null) {
            glow = create(RenderPipelines.DRAGON_RAYS, "glow", null, RenderTypes::dragonRays);
        }
        return glow;
    }

    /** Vanilla's energy swirl without its depth writes; see 26.1.x's copy of this class. */
    static RenderType layers(Identifier texture) {
        if (layers == null) {
            layers = create(RenderPipelines.ENERGY_SWIRL, "layer_glow", texture,
                    () -> RenderTypes.energySwirl(texture, 0.0F, 0.0F));
        }
        return layers;
    }

    /**
     * @param texture  the texture the copy samples, or {@code null} for an untextured one
     * @param fallback vanilla's own material, used if the copy cannot be built
     */
    private static RenderType create(RenderPipeline source, String name, Identifier texture,
            java.util.function.Supplier<RenderType> fallback) {
        try {
            DepthStencilState depth = source.getDepthStencilState();
            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(CrystalTweaksClient.MOD_ID, "pipeline/" + name))
                    .withVertexShader(source.getVertexShader())
                    .withFragmentShader(source.getFragmentShader())
                    .withPolygonMode(source.getPolygonMode())
                    .withCull(source.isCull())
                    .withColorTargetState(source.getColorTargetState())
                    .withVertexBinding(0, source.getVertexFormatBinding(0))
                    .withPrimitiveTopology(source.getPrimitiveTopology())
                    .withDepthStencilState(new DepthStencilState(depth.depthTest(), false,
                            depth.depthBiasScaleFactor(), depth.depthBiasConstant()));
            copyDefines(builder, source.getShaderDefines());
            for (BindGroupLayout layout : source.getBindGroupLayouts()) {
                builder.withBindGroupLayout(layout);
            }
            RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(builder.build());
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
