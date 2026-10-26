[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)
[![GitHub](https://img.shields.io/badge/GitHub-Code-181717?logo=github&logoColor=white)](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks)
[![Report an issue](https://img.shields.io/badge/GitHub-Report%20an%20issue-D73A49?logo=github&logoColor=white)](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/issues/new)
[![Wiki](https://img.shields.io/badge/Wiki-Guide-7B2CBF?logo=readthedocs&logoColor=white)](https://github.com/kerlycanelita/KoHs-Crystal-Tweaks/wiki)

# Crystal Tweaks 2.7.0

## The settings

The settings are a dark book of three pages: the sections on the left, your crystal in the middle
and the open section on the right. The crystal wears everything you set, colours, glow, flash and
sound, at its real size, floating over an obsidian floor before a castle wall.

- Sections: **Colours**, **Converter**, **Sound**, **Glow**, **Flash**, **Helpers**, **Client crystal** and **Advanced**, with **Crystal Practice** and **KoHs** under them.
- **Enemy crystals**, over the sections, is under repair: its switch is dimmed and every crystal uses your own profile for now.
- Left click the crystal to explode it, right click to place it again.
- The settings always open at GUI scale 3, whatever yours is, and give yours back when they close.

![Settings](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/hub-2.7-colours.png)


## Glow

Open the **Glow** section:

- **The glow is in the crystal:** its layers shine, each in its own colour, and the light turns and floats with them.
- **Style:** **Light**, a soft glow round every frame that keeps its colour in daylight, **Layers**, stronger and more saturated, or **Heartbeat**, **Aurora**, **Orbit**, **Flames** and **Sigil**, which add light of their own round the crystal. Each style has settings of its own, such as the heartbeat's rhythm, the orbits' trail or the sigil's points.
- **Power:** from 0 to 300%. An extra glow layer keeps the effect visible in any lighting, not only at night.
- **Core:** how much the core glows, from 0 to 300%, with every kind of glow.
- **Motion blur:** the layers trail behind them as they turn, from 0 to 100%, with the glow on or off.
- **Quality:** Performance, Balanced or Quality decide how much the glow draws. Even on Quality it costs less than the glow of 2.5.0 did.
- **Reflections:** simulates colored light on the top faces of nearby blocks.
- **Custom color:** uses the same color picker as the rest of the menu. It colors the light only; the crystal's own layer colors stay as you set them.
- **Flash**, in its own section: the light a destroyed crystal leaves behind fades smoothly, in one of fourteen shapes, each with settings of its own, sized from 10% to 300%.

The glow respects walls and does not use Vanilla's outline that shows through terrain. Prefer the halo it used to be? **Old KoHs Crystal Glow**, in **Advanced**, brings it back.

![Motion blur](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/glow-blur.png)

![Flash styles](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/flash-styles.png)

![Glow styles](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/hub-2.7-glow-styles.png)

![Flash shapes](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/hub-2.7-shapes.png)

## Converter My Crystal

Turn your crystals into something else: any block, item or entity of your game version, picked from a searchable catalogue in the **Converter** section. It is off until you turn it on.

- The crystal keeps its spin and its float, and follows **Rotation**, **Floating** and the new **Size** (30% to 200%).
- The enemy menu has a Converter of its own, for when it is back from repair.
- Your choice is drawn over Minecraft's own texture and over any resource pack's.
- Drawing only: the crystal stays where it is, with the same box to hit.

![Converter My Crystal](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/converter.png)

## Other players' crystals

**Under repair:** the enemy profile is switched off for now, and every crystal, the enemy's too, is drawn with your own profile. This is how it works when it is back.

Give crystals you did not place their own visual profile, with separate colors, glow and animation speeds so they stand out immediately. Identification is approximate because the server does not tell the client who placed a crystal. Anything that does not match your recent placements—including crystals that were already there when you joined—uses the other-player profile. The server's own acknowledgement of your placement tells your crystal from one that reached the same base first, also in fast bursts. Switch to it with **Enemy crystals** over the sections.

## Works alongside other mods

**The client crystal stands down while another crystal optimizer such as Marlow's is installed:** instant break, instant explosion, ghost crystals and paced placement stay off, and the **Client crystal** section says why. Client-side crystal mods and colour mods do not stop it, and neither do performance mods such as Krypton, Sodium or Lithium. Colours, glow, sounds and the preview are always available.

A conflict monitor warns about possible mod clashes.

## Crystal Practice (experimental)

Practice crystal PvP against a bot in a world of its own, from the **Crystal Practice** button under the crystal in the settings:

- **A bot that plays real techniques**, by difficulty: hit-crystal, respawn anchors and face-placing on Normal; d-taps timed to the end of your damage immunity, safe anchors behind glowstone, top-blocking and hiding in holes on Hard; the butterfly, double anchors, triple taps and chain pops on Extreme. Its techniques cannot be switched off: the menu lists them, with how each one works. It carries your own kit and is held to human timing.
- **Three styles:** a **smart** bot that blocks off with obsidian, hides in holes and pearls away to heal, an **aggressive** one that rushes and never backs off, or a **peaceful** one that moves, dodges and keeps its totem but never attacks. They mine their way out if you box them in, and every pearl is aimed by simulating its flight against the terrain before the throw.
- **Your kit, your layout:** netherite, diamond or iron armour, a Knockback I or II sword, 1 to 20 totems or a full inventory, and presets modelled on MCTiers, MCPVP and PVPHQ. Arrange the inventory in a kit editor with a kit room, **Save** and **Reset**.
- **Four grounds:** a flat of netherite blocks, a hole arena, an almost flat meadow with few trees and no caves in one of eight biomes, or a normal world as Minecraft generates it. Every death starts a new round on a rebuilt arena.
- **Without leaving the server:** on a server, Enter practice opens the practice world in the same window while the server stays connected behind it, and Leave practice puts you back where you were.
- **Training:** drills with a goal and a clock, a report after each round, a round map of every blast that did damage, and a damage map that lights each obsidian in reach by what its blast would do. Practise at a server's delay, from 0 to 300 ms, and copy your server inventory into a kit.

![Crystal Practice](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/practice-gear.png)

![Practice grounds](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/practice-worlds.png)

## Client crystal

Off until you turn it on, in the **Client crystal** section. The crystal you hit disappears from
your screen the moment you hit it, and its explosion, sound and burst, plays right then instead of a
round trip later. It stays in your world, so your next click, your reach and every packet are
Vanilla's, and it shows again if the server refuses the hit (**Refused hit cue** plays a low note
then). **Ghost crystals** draw a stand-in on the obsidian until the server's crystal arrives; it
cannot be hit and no packet names it. **Paced placement** holds a click on the crystal you just
broke until the server removes it, then places: the same Vanilla packet, never ahead of the server,
but it changes when your click goes out, so check your server's rules.

Instant break and instant explosion were tested on a local server with Grim Anticheat and
Ravenclaw's Ping Equalizer, walking and placing obsidian and crystals: no alerts, and the same
packets, cycle for cycle, as Vanilla.

![Client crystal](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/hub-2.7-client-crystal.png)

## In the fight

Under **Helpers**, all off until you turn them on: **Why didn't it place?** says under the crosshair
why a crystal did not appear where you clicked; the **Latency meter** shows beside the hotbar how long
the server took to place and to remove your last crystals; the **Death recap** lists, on the death
screen or in chat, the blasts of your last 8 seconds and whose crystals they were. They only read
what you already see. **Don't break obsidian** and **Obsidian debounce** filter clicks, so they send
fewer actions than Vanilla: leave them off if your server forbids input filters.

![Helpers](https://raw.githubusercontent.com/kerlycanelita/KoHs-Crystal-Tweaks/main/docs/images/hub-2.7-helpers.png)

## Built for fair play

Crystal Tweaks only changes local presentation and local helpers. It does not change reach, camera or cooldowns, and it does not invent or repeat actions; the one setting that changes when a click goes out, Paced placement, is off by default and never places ahead of the server. Crystal Practice and its bot only exist in their own singleplayer worlds. Server rules can differ, so always check the rules of the server you play on.

## Compatibility

Minecraft **1.21.11**, **26.1**, **26.1.1**, **26.1.2**, **26.2** and **26.3** on Fabric. Download the file matching your exact Minecraft version and install the matching Fabric API. Mod Menu is optional.

**Archived versions:** Minecraft **1.21 to 1.21.10** are archived. Their last release, **2.2.7**, stays available for download here, but they no longer receive updates or fixes.
