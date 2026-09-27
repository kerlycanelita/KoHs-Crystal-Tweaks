import com.zymekoh.crystaltweaks.practice.BotSkill;
import com.zymekoh.crystaltweaks.practice.KitItem;
import com.zymekoh.crystaltweaks.practice.KitLayout;
import com.zymekoh.crystaltweaks.practice.KitPreset;
import com.zymekoh.crystaltweaks.practice.PracticeSettings;
import com.zymekoh.crystaltweaks.practice.PracticeTerrain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks Crystal Practice's plain-Java model: the kits and their presets, the totem selector, the
 * stored form of a rearranged kit, the arena's terrain and the bot's skills per difficulty.
 *
 * <p>Run with tools/test-glow.ps1; no Minecraft instance is needed.</p>
 */
public final class PracticeKitTest {
    private static int problems;
    private static int checks;

    public static void main(String[] args) {
        presets();
        totems();
        storage();
        settings();
        skills();
        terrain();
        System.out.println("practice checks: " + checks + "  problems: " + problems);
        if (problems > 0) {
            System.exit(1);
        }
    }

    private static void presets() {
        int[] expectedTotems = {10, -1, 12, 14, 3};
        for (KitPreset preset : KitPreset.values()) {
            KitLayout layout = preset.defaultLayout();
            String tag = preset.name();
            check(!layout.isEmpty(), tag + ": empty kit");
            check(layout.count(KitItem.SWORD) == 1, tag + ": needs exactly one sword");
            check(layout.count(KitItem.PICKAXE) == 1, tag + ": needs one pickaxe");
            check(layout.count(KitItem.END_CRYSTAL) >= 128, tag + ": fewer than two stacks of crystals");
            check(layout.count(KitItem.OBSIDIAN) >= 128, tag + ": fewer than two stacks of obsidian");
            check(layout.count(KitItem.RESPAWN_ANCHOR) >= 64 && layout.count(KitItem.GLOWSTONE) >= 64,
                    tag + ": anchors need glowstone");
            check(layout.get(KitLayout.OFFHAND) != null && layout.get(KitLayout.OFFHAND).item() == KitItem.TOTEM,
                    tag + ": the off hand holds a totem");
            for (int slot = 0; slot < KitLayout.HOTBAR; slot++) {
                check(layout.get(slot) != null, tag + ": hotbar slot " + (slot + 1) + " is empty");
            }
            for (int slot = 36; slot < 40; slot++) {
                check(layout.get(slot) == null, tag + ": armour slot " + slot + " holds an item");
            }
            int expected = expectedTotems[preset.ordinal()];
            if (expected == KitLayout.FULL) {
                check(layout.full(), tag + ": should fill every free slot with totems");
            } else {
                check(layout.totems() == expected, tag + ": " + layout.totems() + " totems, expected " + expected);
            }
            check(!preset.label(true).isBlank() && !preset.label(false).isBlank(), tag + ": missing label");
            check(!preset.note(true).isBlank() && !preset.note(false).isBlank(), tag + ": missing note");
            check(KitPreset.parse(preset.id) == preset, tag + ": id does not parse back");
            check(preset.next().previous() == preset, tag + ": next and previous disagree");
        }
        check(KitPreset.parse("nonsense") == KitPreset.STANDARD, "unknown preset falls back to the standard kit");
    }

    private static void totems() {
        KitLayout standard = KitPreset.STANDARD.defaultLayout();
        KitLayout one = standard.withTotems(1);
        check(one.totems() == 1, "one totem left");
        check(one.get(KitLayout.OFFHAND) != null && one.get(KitLayout.OFFHAND).item() == KitItem.TOTEM,
                "the last totem stays in the off hand");
        check(one.count(KitItem.END_CRYSTAL) == standard.count(KitItem.END_CRYSTAL),
                "changing totems never touches other items");
        KitLayout twenty = standard.withTotems(20);
        check(twenty.totems() == 20, "twenty totems: " + twenty.totems());
        KitLayout full = standard.withTotems(KitLayout.FULL);
        check(full.full(), "full fills every free slot");
        int others = 0;
        for (KitItem item : KitItem.values()) {
            if (item != KitItem.TOTEM) {
                others += countStacks(full, item);
            }
        }
        check(full.totems() + others == KitLayout.INVENTORY + 1, "full kit uses all 37 slots: " + (full.totems() + others));
        check(standard.withTotems(1000).full(), "more totems than slots just fills the kit");
        check(standard.withTotems(0).totems() == 1, "never fewer than one totem");
        // The selector walks 1, 2, 3 ... 20, full, and back to 1.
        List<Integer> seen = new ArrayList<>();
        KitLayout walking = standard.withTotems(1);
        for (int step = 0; step < KitLayout.TOTEM_STEPS.length; step++) {
            int next = KitLayout.nextTotemStep(walking, true);
            seen.add(next);
            walking = walking.withTotems(next);
        }
        check(seen.get(seen.size() - 2) == KitLayout.FULL, "selector reaches full: " + seen);
        check(seen.get(seen.size() - 1) == 1, "selector wraps back to one: " + seen);
        check(KitLayout.nextTotemStep(standard.withTotems(7), true) == 8, "7 steps up to 8");
        check(KitLayout.nextTotemStep(standard.withTotems(7), false) == 6, "7 steps down to 6");
        check(KitLayout.nextTotemStep(full, false) == 20, "full steps down to 20");
        check(KitLayout.nextTotemStep(standard.withTotems(1), false) == KitLayout.FULL, "1 steps down to full");
    }

    private static int countStacks(KitLayout layout, KitItem item) {
        int stacks = 0;
        for (int slot = 0; slot < KitLayout.SLOTS; slot++) {
            KitLayout.Entry entry = layout.get(slot);
            if (entry != null && entry.item() == item) {
                stacks++;
            }
        }
        return stacks;
    }

    private static void storage() {
        for (KitPreset preset : KitPreset.values()) {
            KitLayout layout = preset.defaultLayout();
            KitLayout copy = KitLayout.decode(layout.encode());
            check(layout.equals(copy), preset + ": encode and decode lose something");
        }
        KitLayout swapped = KitPreset.STANDARD.defaultLayout();
        swapped.swap(0, 8);
        check(swapped.get(0).item() == KitItem.TOTEM && swapped.get(8).item() == KitItem.SWORD, "swap moves both stacks");
        check(!swapped.equals(KitPreset.STANDARD.defaultLayout()), "a swapped kit differs from the preset");
        check(KitLayout.decode(null) == null && KitLayout.decode("") == null && KitLayout.decode("garbage") == null,
                "unusable text gives no kit");
        KitLayout damaged = KitLayout.decode("0:sword:1, x:y:z, 1:end_crystal:999, 37:totem:1, 1:obsidian:5, 40:totem:1, 2:unknown:3");
        check(damaged != null, "a partly damaged kit still loads");
        if (damaged != null) {
            check(damaged.get(0) != null && damaged.get(0).item() == KitItem.SWORD, "valid entries survive");
            check(damaged.get(1) != null && damaged.get(1).count() == 64, "counts are clamped to a stack");
            check(damaged.get(1).item() == KitItem.END_CRYSTAL, "a repeated slot keeps its first entry");
            check(damaged.get(37) == null, "armour slots are refused");
            check(damaged.get(2) == null, "unknown items are skipped");
            check(damaged.get(KitLayout.OFFHAND) != null, "the off hand loads");
        }
        check(new KitLayout.Entry(KitItem.ENDER_PEARL, 64).count() == 16, "pearls stack to 16");
        check(new KitLayout.Entry(KitItem.TOTEM, 5).count() == 1, "totems do not stack");
        check(new KitLayout.Entry(KitItem.OBSIDIAN, -3).count() == 1, "a stack holds at least one");
    }

    private static void settings() {
        check(PracticeSettings.clampKnockback(0) == 1 && PracticeSettings.clampKnockback(3) == 2, "knockback stays I or II");
        PracticeSettings parsed = PracticeSettings.from("nonsense", 0b1111, true, "nonsense", "nonsense", 9, "nonsense",
                "nonsense", "nonsense");
        check(parsed.armor == PracticeSettings.Armor.NETHERITE, "unknown armour falls back to netherite");
        check(Integer.bitCount(parsed.blastPieces) == 2, "at most two Blast Protection pieces");
        check(parsed.difficulty == PracticeSettings.Difficulty.NORMAL, "unknown difficulty falls back to normal");
        check(parsed.style == PracticeSettings.BotStyle.SMART, "unknown style falls back to the smart bot");
        check(PracticeSettings.from("netherite", 0, true, "hard", "AGGRESSIVE", 1, "standard", "FLAT", "plains").style
                == PracticeSettings.BotStyle.AGGRESSIVE, "the aggressive style parses");
        check(parsed.knockback == 2, "knockback clamps to II");
        check(parsed.preset == KitPreset.STANDARD, "unknown preset falls back");
        check(parsed.worldType == PracticeSettings.WorldType.FLAT, "unknown world falls back to the flat");
        check(parsed.biome == PracticeSettings.Biome.PLAINS, "unknown biome falls back to plains");
        check(parsed.describe(true).contains("Empuje II") && parsed.describe(false).contains("Knockback II"),
                "the description names the sword's knockback");
        Set<String> ids = new HashSet<>();
        for (PracticeSettings.Biome biome : PracticeSettings.Biome.values()) {
            check(ids.add(biome.id), "biome ids are unique: " + biome.id);
            check(biome.id.matches("[a-z_]+"), "biome id is folder-safe: " + biome.id);
            check(PracticeSettings.Biome.parse(biome.id) == biome, "biome id parses back: " + biome.id);
        }
        PracticeSettings.Difficulty previous = null;
        for (PracticeSettings.Difficulty difficulty : PracticeSettings.Difficulty.values()) {
            if (previous != null) {
                check(difficulty.reaction <= previous.reaction && difficulty.step <= previous.step
                        && difficulty.precision >= previous.precision, difficulty + " is not harder than " + previous);
            }
            // Never faster than a player by hand: one action per tick at best.
            check(difficulty.step >= 1 && difficulty.actionDelay >= 1 && difficulty.reaction >= 2,
                    difficulty + " acts faster than a human can");
            previous = difficulty;
        }
    }

    private static void skills() {
        for (PracticeSettings.BotStyle style : PracticeSettings.BotStyle.values()) {
            List<BotSkill> previous = List.of();
            for (PracticeSettings.Difficulty difficulty : PracticeSettings.Difficulty.values()) {
                List<BotSkill> skills = BotSkill.of(difficulty, style);
                check(skills.containsAll(previous), difficulty + " " + style + " loses a skill of the easier difficulty");
                check(skills.size() > previous.size(), difficulty + " " + style + " adds nothing");
                PracticeSettings settings = new PracticeSettings(PracticeSettings.Armor.NETHERITE, 0, true, difficulty, style,
                        1, KitPreset.STANDARD, PracticeSettings.WorldType.FLAT, PracticeSettings.Biome.PLAINS);
                for (BotSkill skill : skills) {
                    check(settings.uses(skill), difficulty + " " + style + " lists a skill it does not use: " + skill);
                }
                previous = skills;
            }
        }
        List<BotSkill> smart = BotSkill.of(PracticeSettings.Difficulty.EXTREME, PracticeSettings.BotStyle.SMART);
        List<BotSkill> aggressive = BotSkill.of(PracticeSettings.Difficulty.EXTREME, PracticeSettings.BotStyle.AGGRESSIVE);
        check(smart.contains(BotSkill.BLOCK_OFF) && smart.contains(BotSkill.PEARL_ESCAPE) && smart.contains(BotSkill.HOLES)
                && !smart.contains(BotSkill.RUSH), "the smart bot covers itself, pearls out and hides, and never rushes");
        check(aggressive.contains(BotSkill.RUSH) && !aggressive.contains(BotSkill.BLOCK_OFF)
                && !aggressive.contains(BotSkill.PEARL_ESCAPE) && !aggressive.contains(BotSkill.HOLES),
                "the aggressive bot rushes and never backs off");
        for (PracticeSettings.BotStyle style : PracticeSettings.BotStyle.values()) {
            check(BotSkill.of(PracticeSettings.Difficulty.EASY, style).contains(BotSkill.MINE_OUT),
                    style + " mines its way out from Easy up");
            check(!style.note(true).isBlank() && !style.note(false).isBlank(), style + ": missing note");
        }
        for (BotSkill skill : BotSkill.values()) {
            check(!skill.label(true).isBlank() && !skill.label(false).isBlank(), skill + ": missing name");
            check(skill.how(true).length() > 20 && skill.how(false).length() > 20, skill + ": missing explanation");
        }
        check(!PracticeSettings.Difficulty.EASY.has(BotSkill.HIT_CRYSTAL), "easy does not hit-crystal");
        check(PracticeSettings.Difficulty.NORMAL.has(BotSkill.ANCHORS), "normal anchors");
        check(PracticeSettings.Difficulty.HARD.has(BotSkill.DOUBLE_TAP) && !PracticeSettings.Difficulty.HARD.has(BotSkill.BUTTERFLY),
                "hard d-taps but does not butterfly");
    }

    private static void terrain() {
        PracticeTerrain terrain = new PracticeTerrain(123456789L);
        PracticeTerrain same = new PracticeTerrain(123456789L);
        PracticeTerrain other = new PracticeTerrain(987654321L);
        int radius = PracticeTerrain.RADIUS;
        boolean differs = false;
        int holes = 0;
        for (int x = -radius - 4; x <= radius + 4; x++) {
            for (int z = -radius - 4; z <= radius + 4; z++) {
                int height = terrain.heightOffset(x, z);
                check(height >= -2 && height <= 2, "hill out of range at " + x + "," + z + ": " + height);
                check(height == same.heightOffset(x, z), "terrain is not deterministic at " + x + "," + z);
                if (height != other.heightOffset(x, z)) {
                    differs = true;
                }
                double distance = Math.sqrt(x * x + z * z);
                if (distance >= radius || distance < 3.0D) {
                    check(height == 0, "ground not flat at " + x + "," + z + " (edge or spawn)");
                }
                boolean hole = terrain.hole(x, z);
                if (hole) {
                    holes++;
                    check(distance >= 4.0D, "hole next to the spawn at " + x + "," + z);
                    check(!terrain.ledge(x, z), "step over a hole at " + x + "," + z);
                    // Holes are at most two wide: never three in a row.
                    check(!(terrain.hole(x + 1, z) && terrain.hole(x + 2, z)), "hole wider than two at " + x + "," + z);
                    check(!(terrain.hole(x, z + 1) && terrain.hole(x, z + 2)), "hole deeper than two at " + x + "," + z);
                }
                check(terrain.plant(x, z, 0.2D, 3) < 3, "plant index out of range");
                check(terrain.plant(x, z, 0.0D, 3) == -1, "plants with zero density");
            }
        }
        check(differs, "different seeds give the same hills");
        check(holes > 100, "hole arena has too few holes: " + holes);
        List<int[]> trees = terrain.trees(0.14D);
        check(trees.size() >= 3 && trees.size() <= 40, "few trees, not none and not a forest: " + trees.size());
        for (int[] tree : trees) {
            double distance = Math.sqrt(tree[0] * tree[0] + tree[1] * tree[1]);
            check(distance >= PracticeTerrain.OPEN_SPAWN + 1, "tree too close to the spawn: " + tree[0] + "," + tree[1]);
            check(Math.abs(tree[0]) <= radius - 4 && Math.abs(tree[1]) <= radius - 4, "tree at the edge");
            check(tree[2] >= 0 && tree[2] < 100, "tree variant out of range");
        }
        check(terrain.trees(0.0D).isEmpty(), "no trees at zero density");
        for (int index = 0; index < 1000; index++) {
            double unit = terrain.unit(index, -index, index % 7);
            check(unit >= 0.0D && unit < 1.0D, "unit out of range: " + unit);
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            problems++;
            if (problems <= 25) {
                System.out.println("  FAIL " + message);
            }
        }
    }
}
