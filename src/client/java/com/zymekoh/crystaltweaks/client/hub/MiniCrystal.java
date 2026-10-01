package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalAppearanceAccess;
import com.zymekoh.crystaltweaks.client.CrystalGlowAccess;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A small End Crystal drawn by the game's own renderer, for icons and scenes: the Colors and Glow
 * panel icons, the crystals of the practice gate and the one that mines in the enemy menu. Each
 * keeps its own render state, so several can be on screen at once in different colours.
 */
public final class MiniCrystal {
    private final EndCrystalRenderState state = new EndCrystalRenderState();
    private final long bornAt = System.nanoTime();
    private final float phase;

    public MiniCrystal(float phase) {
        CrystalStage.prepare(this.state);
        this.phase = phase;
    }

    /**
     * Draws the crystal in a square of {@code size} pixels whose top-left corner is (x, y).
     *
     * @param tilt  how far it leans toward the viewer, in radians
     * @param scale extra size of the model inside its square, 1 for the usual fit
     */
    public void draw(GuiGraphicsExtractor graphics, int x, int y, int size, CrystalAppearance look, long now, float tilt,
            float scale) {
        if (size < 4) {
            return;
        }
        this.state.ageInTicks = (now - this.bornAt) / 50_000_000.0F + this.phase * 40.0F;
        if ((Object) this.state instanceof CrystalAppearanceAccess access) {
            access.crystalTweaks$appearance(look);
        }
        if ((Object) this.state instanceof CrystalGlowAccess glow) {
            glow.crystalTweaks$surfaces(List.of());
        }
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateX(tilt);
        float modelScale = size * CrystalStage.glowScale(look) * scale;
        graphics.entity(this.state, modelScale, new Vector3f(0.0F, 1.0F, 0.0F), rotation, new Quaternionf(), x, y,
                x + size, y + size);
    }

    /** A look whose three layers run through the hues, each a third of the wheel from the others. */
    public static CrystalAppearance rainbow(long now, boolean glow, int haloColor) {
        CrystalAppearance look = new CrystalAppearance();
        float hue = (now / 1_000_000_000.0F) * 0.12F;
        look.outerColor = HubColorPicker.hsvToArgb(hue, 0.62F, 1.0F);
        look.innerColor = HubColorPicker.hsvToArgb(hue + 0.33F, 0.55F, 1.0F);
        look.coreColor = HubColorPicker.hsvToArgb(hue + 0.66F, 0.7F, 1.0F);
        look.rotationSpeedPercent = 160;
        look.floatingSpeedPercent = 60;
        look.glowEnabled = glow;
        look.glowPowerPercent = 85;
        look.glowReflectionsPercent = 0;
        look.customGlowColor = true;
        look.glowColor = haloColor;
        look.flashEnabled = false;
        return look;
    }
}
