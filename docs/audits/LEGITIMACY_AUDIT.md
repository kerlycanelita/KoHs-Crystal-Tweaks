# Client/Server Legitimacy Audit

This audit covers Crystal Tweaks 2.3.0 for Minecraft 1.21.11 and 26.1 through 26.3. It separates
local-only functionality from behavior a server can observe. The 2.2.7 edition described an older
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

## Safe Crystal — L2 input filter, switchable

While an End Crystal is held, a left click on obsidian returns `FAIL` from Fabric's
`AttackBlockCallback`, so Vanilla never starts the block break and no `START_DESTROY_BLOCK` is
sent. This withholds an action rather than adding one, and it cannot break a block the player could
not otherwise break. The first click still swings, as Vanilla does when an attack starts nothing.

It is on by default and can be switched off in **Advanced**. It also stands down whenever another
crystal optimizer is detected.

## Crystal ownership colours — L0 local heuristic

The crystal that appears on a base after one of the player's own placements is drawn with the
player's profile; every other crystal uses the enemy profile. The server does not send ownership,
so this is an approximation built only from the player's own actions. It selects colours and nothing
else.

## Glow, reflections and afterglow — L0 cosmetic

The halo, the coloured spill on block tops and the flash a destroyed crystal leaves are drawn with
Vanilla's additive dragon-ray material with the depth test on: nothing shows through walls. The glow
raises the crystal's own block light so it reads in the dark, the way an emissive resource pack
does. Terrain under a crystal is sampled only to place the spill, within a small budget per tick.

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
use packet is sent. Like Safe Crystal it withholds an action and never adds, delays or repeats one.

## Force off, benchmark and Crystal Practice — L0

Force off only removes this mod's own helpers. The benchmark reads packets Vanilla already sends and
receives, after the fact, and never creates or changes one. Crystal Practice runs only in its own
singleplayer world; its bot and kit live on that integrated server and never on a multiplayer one.

## Conflict Monitor and optimizer detection — L0 local only

The monitor and the detector read installed Fabric metadata, Mixin configuration JSON and class
annotations from the local filesystem. They send nothing, advertise nothing and never modify another
mod. When another crystal optimizer is found, every interaction helper above stands down; visuals
and sounds do not.

## What this mod does not do

- It does not change `rightClickDelay`, attack cooldown, reach, rotation, or any other rate or range
  limit.
- It does not place, break, target or aim at anything on the player's behalf.
- It does not send, delay, reorder, duplicate or rewrite any gameplay packet.
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
| `START_DESTROY_BLOCK` on obsidian with a crystal held | Withheld while Safe Crystal is on. |
| An obsidian placement inside the debounce window | Withheld while Obsidian debounce is on. |

Those last two rows are the only differences in the packet stream. No anticheat penalises a player
for not mining or placing a block, but a server that forbids input filters can ask players to switch
Safe Crystal and Obsidian debounce off.

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
