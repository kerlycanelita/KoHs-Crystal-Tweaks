import com.zymekoh.crystaltweaks.client.CrystalAppearance;
import com.zymekoh.crystaltweaks.client.CrystalFlashStyle;
import com.zymekoh.crystaltweaks.client.CrystalScreenLayout;
import com.zymekoh.crystaltweaks.client.CrystalGlowMath;
import com.zymekoh.crystaltweaks.client.AfterglowTimeline;
import com.zymekoh.crystaltweaks.client.benchmark.BenchmarkStats;
import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import com.zymekoh.crystaltweaks.practice.PracticeSettings;
import java.util.List;
import java.util.UUID;

/** Run using tools/test-glow.ps1; no Minecraft instance or network required. */
public final class CrystalAppearanceTest {
    public static void main(String[] args) {
        check(CrystalOptimizerGuard.scanPending() && !CrystalOptimizerGuard.optimizationsAllowed(),
                "Interaction helpers start disabled while compatibility is unknown");
        CrystalOptimizerGuard.completeScan();
        check(CrystalOptimizerGuard.optimizationsAllowed(), "A completed clean scan enables helpers");
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
        CrystalAppearance fresh = new CrystalAppearance();
        check(fresh.glowPowerPercent == 55 && fresh.glowReflectionsPercent == 55,
                "Glow ships on at 55/55 so the mod is visible from the first launch");
        check(fresh.customGlowColor && fresh.glowColor == 0xFFC880FF,
                "A fresh profile glows crystal purple");
        check(fresh.flashScalePercent == 100, "Flash size starts at its natural scale");
        fresh.flashScalePercent = 9999;
        check(fresh.copy().flashScalePercent == 300, "Flash size clamps at 300");
        fresh.flashScalePercent = -5;
        check(fresh.copy().flashScalePercent == CrystalAppearance.MIN_FLASH_SCALE
                && CrystalAppearance.MIN_FLASH_SCALE == 10, "Flash size goes down to 10 and clamps there");
        CrystalAppearance layered = new CrystalAppearance();
        layered.outerColor = 0xFF00FF00;
        layered.customGlowColor = true;
        layered.glowColor = 0xFF0000FF;
        check(layered.copy().outerColor == 0xFF00FF00,
                "A custom glow colour must never overwrite a layer colour");
        check(layered.haloColor() == 0xFF0000FF, "The custom colour still drives the halo");
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
        check(timeline.samples(600_000_000).get(0).progress() == 0.5F, "Progress follows the fade clock");
        check(CrystalGlowMath.progress(-5) == 0 && CrystalGlowMath.progress(CrystalGlowMath.FADE_NANOS * 2) == 1,
                "Progress is clamped to the flash's lifetime");
        check(timeline.samples(CrystalGlowMath.FADE_NANOS).isEmpty(), "Expired glow released");
        for (int n = 0; n < 1000; n++) timeline.start(UUID.randomUUID(), "rapid clicks", n);
        check(timeline.size() == 4, "Rapid placement remains bounded");
        timeline.clear(); timeline.start(id, "prediction", 0); timeline.cancel(id);
        check(timeline.size() == 0, "Rejected prediction/reappearing crystal cancels afterglow");
        timeline.start(id, "old world", 0); timeline.clear(); check(timeline.size() == 0, "Disconnect clears light");
        check(CrystalOptimizerGuard.looksLikeOptimizer("marlowcrystal", ""), "Marlow detected by exact id");
        check(CrystalOptimizerGuard.looksLikeOptimizer("nocrystalbreak", ""), "No Crystal Break detected by exact id");
        check(CrystalOptimizerGuard.looksLikeOptimizer("kohs_crystal_tweaks", "KoHs Crystal Tweaks"), "Retired KoHs Crystal Tweaks detected by exact id");
        check(CrystalOptimizerGuard.looksLikeOptimizer("unknown", "Crystal Optimizer"), "Unknown optimizer detected by name");
        check(!CrystalOptimizerGuard.looksLikeOptimizer("krypton", "Krypton"), "Network performance mods are not crystal optimizers");
        // The popular client-side crystal mods, by the ids in their own fabric.mod.json.
        check(CrystalOptimizerGuard.looksLikeOptimizer("clientsidecrystals", "Client Side Crystals"),
                "Client Side Crystals draws its own placement stand-in, so the helpers yield");
        check(CrystalOptimizerGuard.looksLikeOptimizer("clientsidedcrystals", "Client-Sided Crystals"),
                "Client-Sided Crystals detected by exact id");
        check(CrystalOptimizerGuard.looksLikeOptimizer("hcscr", "HCsCR"), "HCsCR detected though its name says nothing");
        for (String name : new String[] {"Kind's Crystal Optimizer", "G1ax Crystal Optimizer",
                "Shikaru's Crystal Optimizer", "Hazel Crystal Optimizer - HCO", "Psychodreams CrystalOptimizer",
                "Rawnet's Crystal Optimizer", "Ryuu Crystal Optimizer", "Akinoko Crystal Optimizer"}) {
            check(CrystalOptimizerGuard.looksLikeOptimizer("unknown_id", name), name + " detected by name");
        }
        // Mods about End Crystals that optimize nothing must never stand the helpers down.
        for (String[] visual : new String[][] {{"fastcrystalspin", "FastCrystalSpin"},
                {"crystal_speed", "Crystal Speed"}, {"custom_end_crystals", "Custom End Crystals"},
                {"crystalglow", "Crystal Glow"}, {"smallercrystals", "Smaller Crystals"},
                {"safecrystal", "Safe Crystals"}, {"crystal_anchor_counter", "Crystal Anchor Counter"},
                {"cps", "crystals-per-second"}, {"clickcrystals", "ClickCrystals"},
                {"no_end_crystal_damage", "No End Crystal Damage"}}) {
            check(!CrystalOptimizerGuard.looksLikeOptimizer(visual[0], visual[1]),
                    visual[1] + " is not a crystal optimizer by name");
        }
        // The overlap rule, against the mods that actually sit on Connection.send in a real pack.
        List<String> onSend = List.of("me.example.mixin.ConnectionMixin");
        check(!CrystalOptimizerGuard.overlapOptimizesCrystals("krypton", "Krypton", onSend),
                "Krypton shares Connection.send without optimizing crystals");
        check(!CrystalOptimizerGuard.overlapOptimizesCrystals("viafabricplus", "ViaFabricPlus", onSend),
                "A protocol translator is not a crystal optimizer");
        check(!CrystalOptimizerGuard.overlapOptimizesCrystals("betterping", "Better Ping Display", onSend),
                "A ping readout is not a crystal optimizer");
        check(CrystalOptimizerGuard.overlapOptimizesCrystals("marlowcrystal", "Marlow's Crystal Optimizer", onSend),
                "A crystal optimizer on the same path is a conflict");
        check(CrystalOptimizerGuard.overlapOptimizesCrystals(
                        "kohs_crystal_tweaks",
                        "KoHs Crystal Tweaks",
                        List.of("dev.zymekoh.kohscrystaltweaks.mixin.ClientConnectionMixin")),
                "The retired build is caught by id");
        check(CrystalOptimizerGuard.overlapOptimizesCrystals(
                        "unnamed",
                        "Unnamed",
                        List.of("com.example.mixin.EndCrystalAttackMixin")),
                "A crystal Mixin gives away a mod whose name does not");
        check(!CrystalOptimizerGuard.overlapOptimizesCrystals("crystalskins", "Crystal Skins", List.of()),
                "An overlap away from the interaction path is not a conflict, whatever the mod is called");
        check(!CrystalOptimizerGuard.overlapOptimizesCrystals("crystal_anchor_counter", "Crystal Anchor Counter",
                        List.of("com.example.mixin.CrystalCountMixin", "com.example.mixin.ConnectionMixin")),
                "A crystals-per-second counter reads the send path without optimizing anything");
        check(!CrystalOptimizerGuard.overlapOptimizesCrystals("custom_end_crystals", "Custom End Crystals",
                        List.of("com.example.mixin.EndCrystalRendererMixin")),
                "A crystal skin on the renderer is not an optimizer");
        check(!CrystalOptimizerGuard.overlapOptimizesCrystals("fastcrystalspin", "FastCrystalSpin",
                        List.of("com.example.mixin.EndCrystalRendererMixin")),
                "A faster spin is not a faster crystal");
        check(!CrystalOptimizerGuard.overlapOptimizesCrystals("clickcrystals", "ClickCrystals", onSend),
                "A PvP client on the send path is not a crystal optimizer by being called ClickCrystals");
        check(CrystalOptimizerGuard.overlapOptimizesCrystals("lunartweaks", "LunarTweaks",
                        List.of("dev.example.lunartweaks.mixin.CrystalOptimizerMixin")),
                "A crystal optimizer module inside a larger mod gives itself away by its Mixin");
        check(!CrystalOptimizerGuard.overlapOptimizesCrystals("krypton", "Krypton", null),
                "A missing overlap list must not be read as a conflict");
        // Performance mods share this mod's Mixin targets and must never stand it down.
        for (String performanceMod : new String[] {"krypton", "lithium", "sodium", "c2me",
                "immediatelyfast", "scalablelux", "ferritecore", "moreculling", "entityculling",
                "modernfix", "viafabricplus", "viaversion"}) {
            check(CrystalOptimizerGuard.optimizesTheGame(performanceMod),
                    performanceMod + " must be known as a game optimizer");
            check(!CrystalOptimizerGuard.overlapOptimizesCrystals(performanceMod, performanceMod, onSend),
                    performanceMod + " on Connection.send must not disable the crystal optimizer");
            check(!CrystalOptimizerGuard.looksLikeOptimizer(performanceMod, performanceMod),
                    performanceMod + " must not be detected by name");
        }
        check(!CrystalOptimizerGuard.optimizesTheGame("marlowcrystal"), "Marlow is not a game optimizer");
        check(!CrystalOptimizerGuard.optimizesTheGame("kryptonite"), "Allowlist matches exact ids only");
        check(CrystalOptimizerGuard.overlapOptimizesCrystals("SOME_CRYSTAL_OPTIMIZER", null, onSend),
                "Detection is case-insensitive and survives a null name");
        check(!CrystalOptimizerGuard.overlapOptimizesCrystals("SOME_CRYSTAL_MOD", null, onSend),
                "Saying crystal on the send path is not enough");
        CrystalOptimizerGuard.reportConflict("Marlow");
        check(!CrystalOptimizerGuard.optimizationsAllowed(), "Interaction core yields");
        CrystalOptimizerGuard.completeScan();
        check(!CrystalOptimizerGuard.optimizationsAllowed(), "Late scan completion cannot override a conflict");
        CrystalOptimizerGuard.reportScanIncomplete();
        check(CrystalOptimizerGuard.conflictDetected() && CrystalOptimizerGuard.conflictingModName().equals("Marlow"),
                "An incomplete scan must preserve the known conflicting mod");
        check(player.haloColor() == 0xFFFF0000 && CrystalGlowMath.power(300) == 3, "Visuals stay independent of optimizer guard");
        CrystalOptimizerGuard.clearConflict();
        check(CrystalOptimizerGuard.scanPending() && !CrystalOptimizerGuard.optimizationsAllowed(),
                "A re-scan starts from the unknown state");
        CrystalOptimizerGuard.reportScanIncomplete();
        check(CrystalOptimizerGuard.scanIncomplete() && CrystalOptimizerGuard.optimizationsAllowed(),
                "An unreadable mod is not a conflict, so the native optimizer stays on");
        check(!CrystalOptimizerGuard.conflictDetected(), "An incomplete scan must not be reported as a conflict");
        CrystalOptimizerGuard.completeScan();
        check(CrystalOptimizerGuard.scanIncomplete(), "A settled result is not overwritten by a late callback");
        CrystalOptimizerGuard.clearConflict();
        CrystalOptimizerGuard.completeScan();
        check(CrystalOptimizerGuard.optimizationsAllowed() && !CrystalOptimizerGuard.scanIncomplete(),
                "A clean re-scan restores the native optimizer");
        // Safe Crystal reads exactly this flag, so the obsidian helper follows the same rule.
        check(CrystalOptimizerGuard.optimizationsAllowed(),
                "Safe Crystal keeps working when only performance mods are installed");
        CrystalOptimizerGuard.reportConflict("Marlow's Crystal Optimizer");
        check(!CrystalOptimizerGuard.optimizationsAllowed(),
                "Safe Crystal and every other interaction helper stand down for Marlow");
        CrystalOptimizerGuard.clearConflict();
        CrystalOptimizerGuard.completeScan();
        check(player.haloColor() == 0xFFFF0000, "Visual settings remain available whatever the guard decides");
        // Flash styles: fourteen shapes, a stable storage key each, and a round trip in both directions.
        CrystalFlashStyle[] styles = CrystalFlashStyle.values();
        check(styles.length == 14, "Four original flash styles plus ten new ones");
        for (CrystalFlashStyle style : styles) {
            check(CrystalFlashStyle.parse(style.storageKey(), null) == style, style + " survives a save and load");
            check(style.next().previous() == style && style.previous().next() == style, style + " cycles both ways");
            check(!style.label(true).isBlank() && !style.label(false).isBlank(), style + " has both labels");
            check(style.scalable() == (style != CrystalFlashStyle.EXPLOSION), style + " scalability (migration only)");
        }
        check(CrystalFlashStyle.parse("steve", null) == CrystalFlashStyle.MY_HEAD, "The retired Steve head maps to My head");
        check(CrystalFlashStyle.parse("no-such-style", CrystalFlashStyle.EXPLOSION) == CrystalFlashStyle.EXPLOSION,
                "Unknown styles fall back");
        glowAndFlash();
        forceOff();
        practice();
        benchmarkStats();
        int layouts = 0;
        for (int width = 1; width <= 1920; width += 7) for (int height = 1; height <= 1080; height += 7) {
            var l = CrystalScreenLayout.fit(width, height);
            check(l.panel.x() >= 0 && l.panel.right() <= Math.max(1, width), "Panel X bounds");
            check(l.panel.y() >= 0 && l.panel.bottom() <= Math.max(1, height), "Panel Y bounds");
            check(l.optionsX >= l.content.x() && l.optionsX + l.optionsWidth <= l.content.right(), "Options bounds");
            if (l.preview.width() > 0) {
                check(l.preview.x() >= l.optionsX + l.optionsWidth, "No preview/control overlap");
                check(l.preview.right() <= l.content.right(), "Preview bounds");
            } else {
                check(l.optionsWidth == l.content.width(), "Compact layout must reclaim preview space");
            }
            layouts++;
        }
        System.out.println("PASS: appearance, 0/100/300 gain, alpha/light bounds, fade lifecycle, optimizer isolation and " + layouts + " responsive layouts");
    }

    /** The glow switch, the flash's own settings and the migration of profiles saved before them. */
    private static void glowAndFlash() {
        CrystalAppearance fresh = new CrystalAppearance();
        check(fresh.glowEnabled && fresh.glowActive(), "Glow ships switched on");
        check(fresh.flashEnabled && fresh.flashActive(), "The flash ships switched on");
        check(fresh.flashOpacityPercent == 50 && fresh.flashDurationMillis == 1200, "Flash defaults: 50% and 1.2 s");
        check(Math.abs(CrystalGlowMath.flashGain(50) - 0.55F) < 0.001F,
                "50% opacity draws exactly 2.3.0's default flash");
        check(Math.abs(CrystalGlowMath.flashGain(100) - 3F) < 0.001F, "100% opacity is the old 300% power");
        check(CrystalGlowMath.flashGain(0) == 0F, "0% opacity is no light");
        float last = -1;
        for (int opacity = 0; opacity <= 100; opacity++) {
            float gain = CrystalGlowMath.flashGain(opacity);
            check(gain > last || opacity == 0, "Flash gain rises with opacity");
            last = gain;
        }
        check(CrystalGlowMath.opacityForPower(0.55F) == 50 && CrystalGlowMath.opacityForPower(3F) == 100,
                "The inverse recovers the opacity for the shipped and the maximum power");
        check(Math.abs(CrystalGlowMath.FLASH_BASE_RADIUS - CrystalGlowMath.radius(0.55F)) < 1.0E-6F,
                "100% flash size is 2.3.0's default flash size");
        CrystalAppearance off = new CrystalAppearance();
        off.glowEnabled = false;
        check(!off.glowActive() && off.flashActive(), "Switching the glow off leaves the flash alone");
        off.glowEnabled = true;
        off.flashEnabled = false;
        check(off.glowActive() && !off.flashActive(), "Switching the flash off leaves the glow alone");
        CrystalAppearance wild = new CrystalAppearance();
        wild.flashOpacityPercent = 999;
        wild.flashDurationMillis = -5;
        wild.glowEnabled = false;
        CrystalAppearance clamped = wild.copy();
        check(clamped.flashOpacityPercent == 100 && clamped.flashDurationMillis == CrystalAppearance.MIN_FLASH_DURATION,
                "Flash opacity and duration clamp");
        check(!clamped.glowEnabled, "The glow switch survives a copy");
        check(new CrystalAppearance().flashDurationNanos() == 1_200_000_000L, "Duration in nanoseconds");

        // A 2.3.0 profile at the shipped settings migrates to the new defaults, unchanged on screen.
        CrystalAppearance shipped = new CrystalAppearance();
        shipped.migrateLegacyGlow(false, false, false);
        check(shipped.glowEnabled && shipped.flashEnabled && shipped.flashOpacityPercent == 50
                && shipped.flashScalePercent == 100, "Shipped 2.3.0 settings migrate to the new defaults");
        // The owner's own settings: 300% power, skull at 20%.
        CrystalAppearance loud = new CrystalAppearance();
        loud.glowPowerPercent = 300;
        loud.flashScalePercent = 20;
        loud.migrateLegacyGlow(false, false, true);
        float oldRadius = CrystalGlowMath.radius(3F) * 0.2F;
        check(loud.flashOpacityPercent == 100, "300% power becomes the brightest flash");
        check(Math.abs(loud.flashRadius() - oldRadius) < 0.02F, "The migrated flash keeps its old radius");
        CrystalAppearance dark = new CrystalAppearance();
        dark.glowPowerPercent = 0;
        dark.migrateLegacyGlow(false, false, false);
        check(!dark.glowEnabled && !dark.flashEnabled && dark.glowPowerPercent == 55,
                "Power 0 meant both off; switching the glow back on shows the shipped power");
        CrystalAppearance current = new CrystalAppearance();
        current.glowEnabled = false;
        current.flashOpacityPercent = 80;
        current.migrateLegacyGlow(true, true, true);
        check(!current.glowEnabled && current.flashOpacityPercent == 80, "A current profile is left as it is");

        AfterglowTimeline<String> timeline = new AfterglowTimeline<>(4);
        java.util.UUID id = java.util.UUID.randomUUID();
        timeline.start(id, "long", 0L, 3_000_000_000L);
        check(Math.abs(timeline.samples(1_500_000_000L).get(0).progress() - 0.5F) < 1.0E-6F,
                "A light fades on its own duration");
        check(timeline.samples(2_999_000_000L).size() == 1 && timeline.samples(3_000_000_000L).isEmpty(),
                "A light expires at its own duration");
    }

    /** The player's own switch outranks the scan and names itself as the reason. */
    private static void forceOff() {
        CrystalOptimizerGuard.clearConflict();
        CrystalOptimizerGuard.completeScan();
        check(CrystalOptimizerGuard.pauseReason() == CrystalOptimizerGuard.PauseReason.NONE, "Nothing paused");
        CrystalOptimizerGuard.setForcedOff(true);
        check(!CrystalOptimizerGuard.optimizationsAllowed(), "Force off stops every helper");
        check(CrystalOptimizerGuard.pauseReason() == CrystalOptimizerGuard.PauseReason.FORCED_OFF, "Force off is the reason");
        CrystalOptimizerGuard.reportConflict("Marlow's Crystal Optimizer");
        check(CrystalOptimizerGuard.pauseReason() == CrystalOptimizerGuard.PauseReason.FORCED_OFF,
                "The player's own switch is named before a detected mod");
        CrystalOptimizerGuard.setForcedOff(false);
        check(CrystalOptimizerGuard.pauseReason() == CrystalOptimizerGuard.PauseReason.CONFLICT
                && !CrystalOptimizerGuard.optimizationsAllowed(), "Without force off the conflict still holds");
        CrystalOptimizerGuard.clearConflict();
        check(CrystalOptimizerGuard.pauseReason() == CrystalOptimizerGuard.PauseReason.CHECKING, "A re-scan is the reason while it runs");
        CrystalOptimizerGuard.completeScan();
        check(CrystalOptimizerGuard.optimizationsAllowed(), "A clean scan without force off restores the helpers");
    }

    /** Blast Protection never ends up on more than two pieces, whatever the file says. */
    private static void practice() {
        for (int mask = 0; mask < 16; mask++) {
            int clean = PracticeSettings.sanitize(mask);
            check(Integer.bitCount(clean) <= 2 && (clean & ~mask) == 0, "Blast pieces " + mask + " sanitize to at most two of them");
        }
        PracticeSettings settings = PracticeSettings.from("diamond", 0b1111, true, "hard", "smart", 1, "standard", "FLAT", "plains");
        check(settings.armor == PracticeSettings.Armor.DIAMOND && settings.difficulty == PracticeSettings.Difficulty.HARD,
                "Stored names parse case-insensitively");
        check(Integer.bitCount(settings.blastPieces) == 2, "A tampered file cannot give four blast pieces");
        PracticeSettings unknown = PracticeSettings.from("gold", 0, false, "impossible", "smart", 1, "standard", "FLAT", "plains");
        check(unknown.armor == PracticeSettings.Armor.NETHERITE && unknown.difficulty == PracticeSettings.Difficulty.NORMAL,
                "Unknown values fall back to Netherite and Normal");
        PracticeSettings.Difficulty level = PracticeSettings.Difficulty.EASY;
        for (int step = 0; step < PracticeSettings.Difficulty.values().length; step++) {
            check(level.reaction >= level.next().reaction || level.next() == PracticeSettings.Difficulty.EASY,
                    "Each difficulty reacts at least as fast as the one before");
            level = level.next();
        }
        check(level == PracticeSettings.Difficulty.EASY, "Difficulty cycles back to Easy");
        check(!settings.describe(true).isBlank() && !settings.describe(false).isBlank(), "Settings describe themselves");
    }

    private static void benchmarkStats() {
        BenchmarkStats stats = BenchmarkStats.of(new double[] {5, 1, 4, 2, 3}, 5);
        check(stats.count() == 5 && stats.median() == 3 && stats.min() == 1 && stats.max() == 5, "Median and bounds");
        check(Math.abs(stats.mean() - 3) < 1.0E-9, "Mean");
        check(stats.p95() == 5 && stats.p99() == 5, "Nearest-rank percentiles");
        check(Math.abs(stats.stdDev() - Math.sqrt(2)) < 1.0E-9, "Population standard deviation");
        check(!BenchmarkStats.of(new double[0], 0).present(), "No samples is not a zero");
        double[] hundred = new double[100];
        for (int i = 0; i < 100; i++) hundred[i] = i + 1;
        BenchmarkStats spread = BenchmarkStats.of(hundred, 100);
        check(spread.p95() == 95 && spread.p99() == 99 && spread.median() == 50, "Percentiles of 1..100");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
