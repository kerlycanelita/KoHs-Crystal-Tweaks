package com.zymekoh.crystaltweaks.practice;

import java.util.ArrayList;
import java.util.List;

/**
 * What the practice bot knows how to do. Each skill comes with its difficulty, some only with one
 * of the two styles, and none can be turned off: the setup screen lists them instead, with how each
 * technique works, so the player knows what is coming and can practise the same thing.
 *
 * <p>The techniques and their numbers come from the community's own guides: the Simply Vanilla and
 * GenesisEC wikis, the tier-list tutorials of 2025 and 2026 and their technique write-ups (the
 * two-tick obsidian crystal, the three-tick anchor, "d-tap HP" at seven hearts). Every one of them is
 * something a player can do with a Vanilla client; the bot is held to human timing.</p>
 */
public enum BotSkill {
    MOVEMENT(PracticeSettings.Difficulty.EASY, null, "Se mueve y esquiva", "Moves and dodges",
            "Hace strafe a los lados, salta al chocar y se aparta de los cristales que lo dañarían a él.",
            "Strafes, jumps into walls and steps away from crystals that would hurt it."),
    CRYSTALS(PracticeSettings.Difficulty.EASY, null, "Coloca y rompe cristales", "Places and breaks crystals",
            "Solo sobre obsidiana o bedrock, y solo cuando el cristal te daña más a ti que a él.",
            "Only on obsidian or bedrock, and only when the crystal hurts you more than it."),
    OBSIDIAN(PracticeSettings.Difficulty.EASY, null, "Pone obsidiana junto a ti", "Places obsidian next to you",
            "En el piso de netherite no hay base para cristales: la obsidiana junto a tus pies lo es.",
            "A netherite floor takes no crystals: obsidian by your feet is the base."),
    TOTEMS(PracticeSettings.Difficulty.EASY, null, "Recoloca tótems", "Re-equips totems",
            "Vuelve a poner un tótem en la mano izquierda tras cada pop; más rápido cuanto más difícil.",
            "Puts a totem back in its off hand after every pop, faster on harder settings."),
    GAPPLES(PracticeSettings.Difficulty.EASY, null, "Come manzanas doradas", "Eats golden apples",
            "Come cuando le queda poca vida: el inteligente después de poner distancia, el agresivo sin dejar de avanzar.",
            "Eats when its health runs low: the smart one after making distance, the aggressive one without giving ground."),
    MINE_OUT(PracticeSettings.Difficulty.EASY, null, "Mina para salir", "Mines its way out",
            "Si lo encierras entre bloques o le tapas el paso, rompe con el pico el que le estorba, con el tiempo de minado de Vanilla.",
            "Box it in or block its way and it breaks the block in the way with its pickaxe, at Vanilla's mining speed."),
    RUSH(PracticeSettings.Difficulty.EASY, PracticeSettings.BotStyle.AGGRESSIVE, "Rush", "Rush",
            "Nunca se retira: se te pega, busca el golpe y el hit-crystal sin parar y acepta cambiar vida por vida si lleva tótem.",
            "Never backs off: stays on you, goes for the hit and the hit-crystal non-stop and trades health if it holds a totem."),
    HIT_CRYSTAL(PracticeSettings.Difficulty.NORMAL, null, "Hit-crystal", "Hit-crystal",
            "Te golpea con la espada de Empuje y, mientras estás en el aire, detona un cristal debajo: en el aire todo tu cuerpo queda expuesto.",
            "Hits you with the Knockback sword and blows a crystal under you while you are airborne, when all of you is exposed."),
    ANCHORS(PracticeSettings.Difficulty.NORMAL, null, "Anclas de reaparición", "Respawn anchors",
            "Coloca un ancla, la carga con piedra luminosa y la detona a tu lado: fuera del Nether explotan.",
            "Places an anchor, charges it with glowstone and sets it off next to you: outside the Nether they explode."),
    FACEPLACE(PracticeSettings.Difficulty.NORMAL, null, "Faceplace", "Face-place",
            "Si te escondes en un hoyo, pone cristales a la altura de tu cara para seguir dañándote.",
            "If you hide in a hole, it places crystals at face height to keep hurting you."),
    PEARL_CHASE(PracticeSettings.Difficulty.NORMAL, null, "Perlas para acercarse", "Pearls in",
            "Si te alejas, lanza una perla de ender hacia ti. Antes de lanzarla simula su vuelo tick a tick contra el terreno, para que no choque con una pared o un árbol.",
            "If you get away, it throws an ender pearl towards you, simulating its flight tick by tick against the terrain first so it does not hit a wall or a tree."),
    BLOCK_OFF(PracticeSettings.Difficulty.NORMAL, PracticeSettings.BotStyle.SMART, "Se cubre con bloques", "Blocks off",
            "Cuando lo amenazas pone un bloque delante, entre tú y él, si tiene suelo donde apoyarlo: tapa las explosiones que vienen de tu lado.",
            "When you threaten it, it puts a block in front, between you and it, where there is ground to set it on: it stops blasts coming from your side."),
    PEARL_ESCAPE(PracticeSettings.Difficulty.NORMAL, PracticeSettings.BotStyle.SMART, "Se retira con perlas", "Pearls out",
            "Para curarse no retrocede saltando: te pone distancia con una perla apuntada al sitio exacto donde caerá, y come allí.",
            "To heal it does not hop backwards: it pearls away, aimed at the exact spot it will land on, and eats there."),
    DOUBLE_TAP(PracticeSettings.Difficulty.HARD, null, "D-tap", "D-tap",
            "Dos cristales en el mismo salto: el primero te quita el tótem y el segundo llega cuando acaba tu medio segundo de invulnerabilidad. Mata con 7 corazones o menos.",
            "Two crystals in one airtime: the first pops your totem, the second lands as your half second of invulnerability ends. Kills at seven hearts or less."),
    SAFE_ANCHOR(PracticeSettings.Difficulty.HARD, null, "Ancla segura", "Safe anchor",
            "Pone piedra luminosa entre él y el ancla antes de detonarla: la explosión solo te alcanza a ti.",
            "Puts glowstone between itself and the anchor before setting it off, so the blast only reaches you."),
    TOPBLOCK(PracticeSettings.Difficulty.HARD, null, "Bloquea tu obsidiana", "Top-blocks",
            "Tapa con piedra luminosa la obsidiana que pongas junto a él, para que no quepa un cristal.",
            "Caps the obsidian you place next to it with glowstone, so no crystal fits there."),
    HOLES(PracticeSettings.Difficulty.HARD, PracticeSettings.BotStyle.SMART, "Busca hoyos", "Hides in holes",
            "Con poca vida se mete en un hoyo: los cristales a ras de suelo no le llegan a los pies.",
            "Low on health, it drops into a hole, where crystals at ground level cannot reach its feet."),
    MENDING(PracticeSettings.Difficulty.HARD, null, "Repara su armadura", "Mends its armour",
            "Lanza botellas de experiencia cuando su armadura está gastada, como harías tú con Reparación.",
            "Throws experience bottles when its armour wears down, as you would with Mending."),
    BUTTERFLY(PracticeSettings.Difficulty.EXTREME, null, "Butterfly", "Butterfly",
            "Tras el d-tap aprovecha que sigues subiendo: pone otra obsidiana encima y te detona otro cristal en el aire.",
            "After the d-tap it uses your rise: another obsidian on top and another crystal in the air."),
    DOUBLE_ANCHOR(PracticeSettings.Difficulty.EXTREME, null, "Doble ancla", "Double anchor",
            "Dos anclas seguidas, la segunda justo al acabar tu invulnerabilidad: tan letal como un d-tap.",
            "Two anchors back to back, the second as your invulnerability ends: as deadly as a d-tap."),
    TRIPLE_TAP(PracticeSettings.Difficulty.EXTREME, null, "Triple tap", "Triple tap",
            "Un tercer cristal en el mismo combo si sigues en el aire.",
            "A third crystal in the same combo while you are still airborne."),
    CHAIN_POP(PracticeSettings.Difficulty.EXTREME, null, "Encadena pops", "Chain pops",
            "Cuando se te activa un tótem, aprieta al instante: lo que llegue antes de que recoloques el siguiente te mata.",
            "When your totem pops it presses at once: anything landing before you re-equip kills you."),
    CRITS(PracticeSettings.Difficulty.EXTREME, null, "Críticos y W-tap", "Crits and W-tap",
            "Salta y golpea al caer (daño x1,5) y reinicia el sprint entre golpes para empujarte más.",
            "Jumps and hits on the way down (x1.5 damage) and resets its sprint between hits for more knockback."),
    PREDICTION(PracticeSettings.Difficulty.EXTREME, null, "Predice tu caída", "Predicts your fall",
            "Calcula dónde vas a estar dentro de unos ticks y pone ahí el cristal.",
            "Works out where you will be a few ticks ahead and puts the crystal there.");

    public final PracticeSettings.Difficulty minimum;
    /** The only style that uses the skill, or {@code null} when both do. */
    public final PracticeSettings.BotStyle only;
    private final String spanish;
    private final String english;
    private final String spanishHow;
    private final String englishHow;

    BotSkill(PracticeSettings.Difficulty minimum, PracticeSettings.BotStyle only, String spanish, String english,
            String spanishHow, String englishHow) {
        this.minimum = minimum;
        this.only = only;
        this.spanish = spanish;
        this.english = english;
        this.spanishHow = spanishHow;
        this.englishHow = englishHow;
    }

    public String label(boolean useSpanish) {
        return useSpanish ? this.spanish : this.english;
    }

    /** How the technique works, which doubles as a tip for doing it yourself. */
    public String how(boolean useSpanish) {
        return useSpanish ? this.spanishHow : this.englishHow;
    }

    public boolean fits(PracticeSettings.BotStyle style) {
        return this.only == null || this.only == style;
    }

    /** Everything a bot of this difficulty and style does, easiest first. */
    public static List<BotSkill> of(PracticeSettings.Difficulty difficulty, PracticeSettings.BotStyle style) {
        List<BotSkill> skills = new ArrayList<>();
        for (BotSkill skill : values()) {
            if (skill.minimum.ordinal() <= difficulty.ordinal() && skill.fits(style)) {
                skills.add(skill);
            }
        }
        return skills;
    }
}
