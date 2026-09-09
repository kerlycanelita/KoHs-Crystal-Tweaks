import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.GlowEditorLayout;
import com.zymekoh.crystaltweaks.client.CrystalGlowMath;
import com.zymekoh.crystaltweaks.client.AfterglowTimeline;
import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import java.util.UUID;

/** Run using tools/test-glow.ps1; no Minecraft instance or network required. */
public final class CrystalAppearanceTest {
    public static void main(String[] args) {
        CrystalAppearance player = new CrystalAppearance();
        CrystalAppearance enemy = new CrystalAppearance();
        player.outerColor = 0xFF123456;
        enemy.outerColor = 0xFFABCDEF;
        player.glowColor = 0xFF7733AA;
        player.customGlowColor = true;
        check(player.haloColor() == 0xFF7733AA, "Custom glow color must win");
        check(player.outerColor == 0xFF123456, "Override must preserve stored layer colors");
        player.customGlowColor = false;
        check(player.haloColor() != 0xFF7733AA, "Disabling must release the override");
        CrystalAppearance frame = player.copy();
        player.outerColor = -1;
        check(frame.outerColor == 0xFF123456, "Deferred render state must be isolated");
        check(enemy.outerColor == 0xFFABCDEF, "Enemy settings must be independent");
        player.rotationSpeedPercent = 999;
        player.floatingSpeedPercent = -10;
        player.glowPowerPercent = -1;
        player.glowReflectionsPercent = 999;
        frame = player.copy();
        check(frame.rotationSpeedPercent == 300 && frame.floatingSpeedPercent == 0, "Animation bounds");
        check(frame.glowPowerPercent == 0 && frame.glowReflectionsPercent == 300, "Glow bounds");
        check(new CrystalAppearance().glowPowerPercent == 0, "Glow defaults off");
        check(CrystalGlowMath.power(100) == 1 && CrystalGlowMath.power(300) == 3, "100 baseline, 300 triple multiplier");
        check(Math.abs(CrystalGlowMath.radius(1) - 1.55F) < 0.00001F, "Original 100 percent halo radius");
        check(CrystalGlowMath.radius(3) > CrystalGlowMath.radius(1), "Stronger halo expands");
        check(CrystalGlowMath.blockLight(300) == 240, "300 percent must not corrupt packed Minecraft light");
        check(CrystalGlowMath.alpha(9) == 255 && CrystalGlowMath.alpha(-2) == 0, "Alpha never wraps at high power");
        player.outerColor = 0xFFFF0000; player.innerColor = -1; player.coreColor = -1;
        check(player.haloColor() == 0xFFFF0000, "Unpainted layers must not desaturate custom color");
        check(CrystalGlowMath.hotColor(0xFF000000) == 0xFF000000, "Black must not emit white light");
        float lastFade = 1;
        for (long elapsed = 0; elapsed <= CrystalGlowMath.FADE_NANOS; elapsed += 1_000_000) {
            float fade = CrystalGlowMath.fade(elapsed);
            check(fade >= 0 && fade <= lastFade, "Smooth monotonic fade"); lastFade = fade;
        }
        check(CrystalGlowMath.fade(0) == 1 && CrystalGlowMath.fade(CrystalGlowMath.FADE_NANOS) == 0, "Fade endpoints");
        AfterglowTimeline<String> timeline = new AfterglowTimeline<>(4);
        UUID id = UUID.randomUUID();
        timeline.start(id, "first", 0);
        timeline.start(id, "duplicate", 600_000_000);
        check(timeline.size() == 1 && timeline.samples(600_000_000).get(0).opacity() == 0.5F, "Duplicate callbacks must not restart fade");
        check(timeline.samples(CrystalGlowMath.FADE_NANOS).isEmpty(), "Expired glow released");
        for (int n = 0; n < 1000; n++) timeline.start(UUID.randomUUID(), "rapid clicks", n);
        check(timeline.size() == 4, "Rapid placement remains bounded");
        timeline.clear(); timeline.start(id, "prediction", 0); timeline.cancel(id);
        check(timeline.size() == 0, "Rejected prediction/reappearing crystal cancels afterglow");
        timeline.start(id, "old world", 0); timeline.clear(); check(timeline.size() == 0, "Disconnect clears light");
        check(CrystalOptimizerGuard.looksLikeOptimizer("marlowcrystal", ""), "Marlow detected by exact id");
        check(!CrystalOptimizerGuard.looksLikeOptimizer("clientsidecrystals", "Clientside Crystals"), "Visual mods are not interaction optimizers");
        CrystalOptimizerGuard.reportConflict("Marlow");
        check(!CrystalOptimizerGuard.optimizationsAllowed(), "Interaction core yields");
        check(player.haloColor() == 0xFFFF0000 && CrystalGlowMath.power(300) == 3, "Visuals stay independent of optimizer guard");
        CrystalOptimizerGuard.clearConflict();
        int layouts = 0;
        for (int width = 1; width <= 1920; width += 7) for (int height = 1; height <= 1080; height += 7) {
            var l = GlowEditorLayout.fit(17, 23, width, height);
            check(l.optionsX() >= 17 && l.optionsX() + l.optionsWidth() <= 17 + width, "Options X bounds");
            check(l.optionsY() >= 23 && l.optionsY() + l.optionsHeight() <= 23 + height, "Options Y bounds");
            check(l.previewX() >= 17 && l.previewX() + l.previewWidth() <= 17 + width, "Preview bounds");
            check(Math.abs(l.previewX() * 2 + l.previewWidth() - (34 + width)) <= 1, "Centered crystal");
            if (l.previewHeight() > 0) check(l.optionsY() >= 23 + l.previewHeight() + 5, "No preview/control overlap");
            else check(l.optionsHeight() == height, "Compact layout must reclaim preview space");
            layouts++;
        }
        System.out.println("PASS: appearance, 0/100/300 gain, alpha/light bounds, fade lifecycle, optimizer isolation and " + layouts + " responsive layouts");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
