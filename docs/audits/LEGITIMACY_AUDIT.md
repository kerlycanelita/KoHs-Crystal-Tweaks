# Client/Server Legitimacy Audit

This audit covers Crystal Tweaks 2.5.0 for Minecraft 1.21.11 and 26.1 through 26.3. It separates
local-only functionality from behavior a server can observe. 2.4.1 added two core optimizations, the
instant explosion and the removal reader, both local; the lab evidence at the end checks the claim
packet by packet on every supported version. The 2.2.7 edition described an older
design that removed the attacked crystal from the level and refreshed the crosshair; that design is
gone, and so is the packet change it caused.

## Trust model

- A server-only anticheat can inspect gameplay packets, their payloads, order and timing, but cannot
  read this local GUI, its scan results, particles, textures, sounds, configuration files or
  physical input devices.
- A mandatory launcher or cooperating client anticheat can inspect installed mod files, hashes and
  Mixin declarations within the permissions granted by the player.

Levels used below: **L0** local only, nothing observable; **L1** local prediction of an action the
player already performed; **L2** changes which packets the client sends.

## Instant crystal break — L1 local prediction

`CrystalAttackOptimizer` sees the genuine Vanilla attack packet at the head of `Connection.send`,
without creating, changing, delaying, duplicating or cancelling it. If the hit is one the server is
expected to accept, the crystal is marked hidden and `EndCrystalRendererMixin` skips drawing it.

The entity stays in the client level exactly as Vanilla left it. The crosshair, the interaction
ranges, the use cooldown and every later packet are the ones an unmodified client would produce. A
hit the server refuses simply makes the crystal visible again when the hidden window ends.

The window is 250 ms on a quick connection and follows the measured placement round trip on a slow
one, up to one second. A hit qualifies only when:

- the crystal is inside the player's entity interaction range, using the same test Vanilla applies
  to an entity under the crosshair;
- the crystal is not already removed;
- the player is alive and not spectating;
- the player's attack damage is above zero.

## Instant explosion — L1 local prediction

When the break prediction above hides a crystal, `InstantExplosion` plays that crystal's explosion
on this client at once: the game's own explosion sound (or the player's custom sound) and burst, at
the crystal, with the volume and pitch spread `ClientPacketListener.handleExplosion` uses. When the
server's explosion packet arrives for the same spot within the prediction window, a Mixin on
`handleExplosion` skips only that packet's sound and burst, so nothing plays twice. The packet's
knockback, its block debris and everything else it carries are handled as Vanilla handles them.

It is gated exactly like the break: no prediction, no early explosion. A hit the server refuses
leaves a sound and a burst that were not real; the crystal then reappears. Nothing is sent, changed
or delayed.

## Removal reader — L0 local reading

`ClientPacketListener.handleRemoveEntities` runs twice in Vanilla: first on the network thread,
which only reschedules it, then on the client thread. On the first pass the removal reader marks the
crystals in the packet, from a set of crystal ids the client thread keeps, as not to be drawn. The
entity is still removed by the client thread as Vanilla removes it; the reader only stops drawing a
crystal the server already removed a frame earlier. It reads a packet the client already received
and sends nothing.

## Ghost crystals — L1 visual prediction, off by default

On 26.1 and later, a player can opt in to a stand-in crystal drawn over a placement until the
server's crystal arrives. The stand-in is an entity object that is never added to any level: it
cannot be targeted, hit, collided with or counted, and no packet can mention it. It expires with the
same window as the break prediction.

## Placement observer — L0 local only

The observer runs at the tail of `Connection.send` and reads a genuine use-on-block packet after the
Vanilla send path. It records a bounded local history used for latency measurement and for telling
the player's own crystals from others'. It never creates, sends, cancels, retries, duplicates or
mutates a packet.

## Don't break obsidian — L2 input filter, switchable

Called Safe Crystal until 2.3.1. While an End Crystal is held, a left click on obsidian returns
`FAIL` from Fabric's `AttackBlockCallback`, so Vanilla never starts the block break and no
`START_DESTROY_BLOCK` is sent. This withholds an action rather than adding one, and it cannot break
a block the player could not otherwise break. The first click still swings, as Vanilla does when an
attack starts nothing.

It is on by default and can be switched off in the **Crystal Tweaks** tab. It also stands down
whenever another crystal optimizer is detected.

## Crystal ownership colours — L0 local heuristic

A crystal that appears on a base the player clicked is drawn with the player's profile; every other
crystal uses the enemy profile. The server does not send ownership, so this is an approximation.
Since 2.6.0 it also reads the block-change acknowledgement the server sends every client for its
own placements (`ClientboundBlockChangedAckPacket`, by sequence number): the last crystal to have
appeared on the base when the click is acknowledged is the player's, and one that reached the base
before it was someone else's. Until the acknowledgement every crystal on a clicked base is taken to
be the player's, so their own is never drawn as an enemy's. Some servers, and proxies in front of
them, acknowledge a placement before they send its crystal: there an acknowledged click still
claims the first crystal to appear on its base since it was sent. Nothing is sent, asked for or delayed;
it selects colours and nothing else. The rules are plain Java, tested on timelines in
`tools/tests/OwnershipLedgerTest.java`.

## Glow, motion blur, reflections and afterglow — L0 cosmetic

The glow of the Light style is soft sheets of light on the faces of the crystal's own boxes and a
round one about its core, baked from the crystal's texture (again when resource packs reload) and drawn with Vanilla's
beacon-beam material: blended, depth-tested, without depth writes. The Layers style is the
crystal's own boxes drawn again with Vanilla's additive energy-swirl material (the charged
creeper's light), depth writes off and the depth test on, with an aura of the same boxes slightly
larger. The motion blur is the sheets at the angles the crystal had a few ticks earlier. Nothing
shows through walls, and the crystal, its hitbox and its position are untouched: all of it only
puts light where the crystal already is. The quality modes change how much of it is drawn. The old halo (Old KoHs Crystal Glow),
the coloured spill on block tops and the flash a destroyed crystal leaves are drawn with Vanilla's
additive dragon-ray material, also depth-tested. The glow raises the crystal's own block light so
it reads in the dark, the way an emissive resource pack does. Terrain under a crystal is sampled
only to place the spill, within a small budget per tick.

## Size and Converter My Crystal — L0 cosmetic

Size scales the drawing of the crystal about the middle of its hitbox. Converter My Crystal draws an
item's model, or the model of an entity that is built on the client and never added to any world,
in the crystal's place, turning and floating as the crystal would. In both cases the entity the
server sent is untouched: its position, its hitbox, what the crosshair picks and every packet are
Vanilla's. Neither shows a crystal the player could not already see, and a converted crystal is
hidden by walls exactly as a crystal is. They do change how conspicuous a crystal is on the
player's own screen, as its colours and its glow do; a server that restricts crystal visuals
restricts these too.

## Flash styles — L0 cosmetic, one with a visibility rule

Every style is geometry drawn for one frame in the camera-facing plane. Thirteen of the fourteen
never read anything but the blast's own position and the player's own skin.

**Lightning** points its bolts at players near the blast, so it is held to a stricter rule. Its
targets are chosen once, when the crystal explodes, and only among players the player could see at
that moment: not the player, not a spectator, not invisible to the player, inside the view cone of
the player's field of view, and with a clear line of sight from the player's eyes to the target's
head or body, where glass counts as clear and solid blocks do not. Every bolt has the same length.
A bolt therefore never points at, or measures the distance to, anyone who was not already on
screen. With nobody in sight it draws a fixed ring.

Before this rule, bolts were aimed at every player the client tracked, invisible and hidden ones
included, and grew with the distance. That version was never published.

## Custom explosion sound — L0 cosmetic

A local sound file replaces the explosion sound of crystals for this client only. It plays at
`4 × volume`, as Vanilla's explosion does at `4`. Volume stops at 100%, so it is never heard further
away than Vanilla's.

## Obsidian debounce — L2 input filter, off by default

A second obsidian placement inside the chosen window returns `FAIL` from `UseBlockCallback`, so no
use packet is sent. Like Don't break obsidian it withholds an action and never adds, delays or
repeats one.

## Force off, benchmark and Crystal Practice — L0

Force off only removes this mod's own helpers. The benchmark reads packets Vanilla already sends and
receives, after the fact, and never creates or changes one. Crystal Practice runs only in its own
singleplayer world; its bot and kit live on that integrated server and never on a multiplayer one.

## Conflict Monitor and optimizer detection — L0 local only

The monitor and the detector read installed Fabric metadata, Mixin configuration JSON and class
annotations from the local filesystem. They send nothing, advertise nothing and never modify another
mod. When another crystal optimizer is found, every optimization and interaction helper above stands
down; visuals and sounds do not. Only real optimizers count: mods that do things with crystals
(protections, glows, skins, spins, sizes, sounds, counters) never do, whatever their Mixins are
called.

## What this mod does not do

- It does not change `rightClickDelay`, attack cooldown, reach, rotation, or any other rate or range
  limit.
- It does not place, break, target or aim at anything on the player's behalf.
- It does not send, delay, reorder, duplicate or rewrite any gameplay packet.
- It does not act on a break before the server confirms it. A crystal the player hit is hidden, not
  removed: it stays in the client's world and in the crosshair's way until the server removes it,
  so a click in that instant is sent at the crystal, as Vanilla sends it. Letting the crosshair
  pass through it would place the next crystal a round trip sooner; that changes which packet the
  client sends, a server can tell, and it was considered for 2.6.0 and left out.
- It does not read server state the Vanilla client is not already given, and it draws nothing that
  reveals an entity the player could not already see.

## What a packet-based anticheat sees

| Signal | Status |
| --- | --- |
| Attack packet content, timing, order, count | Untouched. The mod observes it after Vanilla handed it to the connection. |
| Use and placement packets | Untouched. The crosshair and the targeted entity are Vanilla's. |
| Yaw and pitch | Never written. |
| Reach | Never extended. |
| Attack cooldown, `rightClickDelay`, click rate | Never modified. |
| Sequence numbers and acknowledgement | Vanilla's own prediction machinery, untouched. |
| Explosion sound and burst | Local only. Played at the hit; the server's copy of them is skipped, its knockback is not. |
| Entity removals | Read earlier on the network thread; nothing is sent. |
| `START_DESTROY_BLOCK` on obsidian with a crystal held | Withheld while Don't break obsidian is on. |
| An obsidian placement inside the debounce window | Withheld while Obsidian debounce is on. |

Those last two rows are the only differences in the packet stream. No anticheat penalises a player
for not mining or placing a block, but a server that forbids input filters can ask players to switch
Don't break obsidian and Obsidian debounce off.

## Honest limits of this claim

- Hiding a crystal a moment early is still client-side prediction. Some servers forbid any
  prediction, visual or not.
- The ownership colours can be wrong: a crystal someone else places on your base right after you
  place there can take your colour.
- Client attestation is a separate matter. A launcher or cooperating client anticheat can see the
  installed JAR and its Mixins regardless of behavior, and some servers ban by mod list rather than
  by conduct.

## Server rules still apply

Server rules differ and this project cannot guarantee acceptance by every server or anticheat.
Review the rules of every multiplayer server and obtain staff approval when required. Where a server
forbids client-side prediction of any kind, turn Ghost crystals off and do not use this mod there.

## Lab evidence (2.4.1)

Crystal Lab (in the private KoHs-Debug-Tools repository) runs the Crystal Tweaks development client
against a local Fabric server with Grim Anticheat 2.3.74 on the client's protocol, with Ravenclaw's
Ping Equalizer adding latency. The tester is not an operator, is in survival, cannot be pushed by
explosions (explosion knockback resistance 1) and stands on a bedrock field. A macro walks forward
and to the left and, twenty times per scenario, presses the obsidian key and Use, the crystal key
and Use, then Attack as soon as the crystal is under the crosshair: real key presses, handled by the
game. Each version runs four scenarios: optimizations on and forced off (the same build, so Vanilla
behaviour), each with no added latency and with 100 ms. Every packet the client sends is recorded
as it leaves.

| Version (server) | Grim alerts, 4 scenarios | Hidden after the hit, on / off | Explosion heard, on / off (+100 ms) |
| --- | --- | --- | --- |
| 1.21.11 (1.21.11) | 0 | 0.05 ms / 25.5 ms | 0.19 ms / 133.6 ms |
| 26.1 (26.1.2) | 0 | 0.05 ms / 16.5 ms | 0.20 ms / 133.5 ms |
| 26.2 (26.2) | 0 | 0.06 ms / 49.0 ms | 0.20 ms / about 150 ms |

Medians. On every version each cycle sent the same packets in the same order with the optimizations
on as with them off (hotbar change, use on the obsidian, swing, hotbar change, use, swing, attack,
swing); the only differences were the cycles where the macro itself missed a crystal, in both modes.
26.1.1, 26.1.2 and 26.3 share the code and the Mixin targets of the versions measured, checked by
`tools/verify-mixins.py`; 26.3 has no Grim build yet.

**Other anticheats.** Grim is one server-side anticheat among several, and not the strictest by
reputation. The claim here does not rest on it: a server-side anticheat, Grim, Vulcan, Polar,
Gladiator or any other, only ever sees the packets the client sends, their content, order and
timing. With the optimizations on, those packets are the ones Vanilla sends for the same input, so
there is nothing for any of them to tell apart. What a server-side anticheat cannot see, the hidden
crystal, the sound and the burst, exists only on this screen. A client-side anticheat or a launcher
that inspects installed mods is a different matter: it sees the JAR whatever the mod does.

## Lab evidence (2.6.0)

The same lab, run again on 2.6.0 on 2026-10-09: now on every version with a Grim build, with a
capture before and after each scenario, and logging, for each crystal the macro hits, whose the mod
took it for. Every crystal in the lab is the player's own.

| Version (server) | Grim alerts, 4 scenarios | Hidden after the hit, on / off | Explosion heard, on / off (+100 ms) | Own crystals taken for own |
| --- | --- | --- | --- | --- |
| 1.21.11 (1.21.11) | 0 | 0.09 ms / 33.6 ms | 0.23 ms / 133.3 ms | 80 of 80 |
| 26.1 (26.1.2) | 0 | 0.07 ms / 16.7 ms | 0.29 ms / 125.2 ms | 80 of 80 |
| 26.1.1 (26.1.2) | 0 | 0.09 ms / 41.3 ms | 0.29 ms / 133.5 ms | 80 of 80 |
| 26.1.2 (26.1.2) | 0 | 0.08 ms / 15.9 ms | 0.31 ms / 133.6 ms | 80 of 80 |
| 26.2 (26.2) | 0 | 0.08 ms / 23.6 ms | 0.29 ms / 132.4 ms | 80 of 80 |
| 26.3 (singleplayer: no Grim build, no added latency) | - | 0.06 ms / 15.1 ms | 0.20 ms / 16.2 ms | 61 of 61 |

Medians. With the optimizations on, the client sent the same kinds of packets and the same number
of each as with them off: without added latency, 40 uses on a block, 60 swings and 20 hits on every
version. With 100 ms added, both modes also show the cycles where the macro's swing reached the
block before the crystal had arrived: 17 hits and 6 block actions, and on 26.1.2 the Vanilla run
had 18 and 4. The one cycle that differs is the first of the first scenario, whose hotbar slot was
already selected.

The glow and the ownership colours were changed once more after that run; neither sends, delays or
reads anything new. On 26.2 the lab was run again on the final code with two more scenarios: a
burst on one base, Use pressed every tick and every crystal hit the moment it is under the
crosshair, and the same burst with every placement acknowledged ahead of the server, as a proxy
that answers before the server does. Grim raised no alert in any of the eight scenarios, and every
crystal was taken for the player's own: 40 of 40 in each burst without added latency, 17 of 17 with
100 ms.

## Verification evidence

- Every supported target is compiled from its matching API through `gradle/versions.properties` and
  `tools/build-all.ps1`, and `tools/verify-mixins.py` checks every Mixin target against that
  version's own classes.
- The attack observer sits at the head of `Connection.send` and the placement observer at its tail;
  neither is cancellable.
- The Lightning target filter is `CrystalFlashShapes.visibleBoltTargets`.
- Built JAR hashes are published in `CHECKSUMS.sha256`.
- Runtime PvP verification remains necessary for tap and hold, main hand and off hand, GUI open,
  high ping and remapped input; the build does not launch the game.
