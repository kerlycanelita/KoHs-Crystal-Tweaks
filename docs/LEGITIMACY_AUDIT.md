# Client/Server Legitimacy Audit

This audit covers Crystal Tweaks 2.2.7 for Minecraft 1.21 through 26.2. It separates local-only
functionality from behavior a server can observe.

## Trust model

- A server-only anticheat can inspect gameplay packets, their payloads, order and timing, but cannot
  read this local GUI, its scan results, particles, textures, sounds, configuration files or
  physical input devices.
- A mandatory launcher or cooperating client anticheat can inspect installed mod files, hashes and
  Mixin declarations within the permissions granted by the player.

## Conflict Monitor — L0 local-only

The monitor reads installed Fabric metadata, Mixin configuration JSON and class annotations from the
local filesystem. It compares only normalized target classes and methods, then renders a local,
non-blocking screen.

It does not send a custom payload, advertise scan results, modify a Mixin at runtime, change input,
or invoke a gameplay action. Dynamic Mixin plugins and external bytecode agents may not be visible,
so a clean result is not proof of universal compatibility.

## Placement observer — L0 local-only

Path:

```text
physical input → vanilla logical Use action → vanilla interaction packet
→ Connection.send(Packet) completes → local observer → local tracker
```

The observer runs at the tail of the existing Vanilla send method. Across the supported builds it
only reads a genuine use-on-block packet after the Vanilla send path and records bounded local
state. It never creates, sends, cancels, retries, duplicates or mutates a packet.

The following remain invariant:

- physical and remapped logical input handling;
- packet type, payload, count, order and sequence number;
- use cooldown, reach, rotation and selected inventory slot;
- server acknowledgement and authoritative crystal creation.

A server-only anticheat receives no additional evidence from this observer. An attested client can
identify the installed mod or inspect its Mixin.

## Instant crystal break — L1 local prediction

`CrystalAttackOptimizer` hides a crystal from the local world right after the genuine Vanilla attack
packet has been handed to the connection. The packet itself is never created, changed, delayed,
duplicated or cancelled, and no additional packet is produced. This is a display-timing prediction
of an action the player already performed.

Because the server stays authoritative, a prediction the server refuses leaves an entity the client
can no longer see while the server still tracks it. That crystal then silently blocks every later
placement on the same base until the chunk reloads. Version 2.2.7 constrains the prediction so it
only runs for a hit the server is expected to accept:

- the crystal is inside the entity interaction range, using the same test Vanilla applies to an
  entity under the crosshair. No extra margin is subtracted: the server accepts an attack up to
  three blocks beyond this range (`isWithinAttackRange(..., 3.0)`), so a stricter client-side test
  rejects ordinary hits and makes the break look slower without preventing a real desynchronisation;
- the crystal is not already removed;
- the player is alive and not spectating;
- the player's attack damage is above zero.

The prediction is core behavior and has no setting.

### Why the crosshair is refreshed afterwards

`Minecraft.tick()` calls `pick(1.0F)` before it handles keybinds, so the hit result the Attack and
Use actions read is computed once per tick, before either action runs. Hiding the crystal
invalidates that hit result while the tick is still in progress.

If it is left stale, `Minecraft.startUseItem` still sees the removed crystal as an
`EntityHitResult`, takes the entity branch, fails `isWithinEntityInteractionRange` (which returns
false for a removed entity) and falls through to a plain use-in-air. The placement is discarded and
the four-tick `rightClickDelay` is spent anyway, costing about 200 ms. Players who bind Attack and
Use to separate keys or mouse buttons hit this most, because they can trigger both inside one tick.

Version 2.2.7 therefore re-runs Vanilla's own pick with Vanilla's own argument, rather than the
block-only ray cast used through 2.2.6. That older ray cast never considered entities, so a second
crystal in the line of sight was skipped in favour of the block behind it, and it cleared
`crosshairPickEntity` unconditionally.

This does not rotate the player, does not extend any range, and does not choose a target: it lets
Vanilla re-answer, along the same ray, a question it already answered this tick, against a world the
player just changed. Interaction ranges, entity selection and `crosshairPickEntity` all remain in
Vanilla's hands.

On Minecraft 1.21 through 1.21.11 this is the public `GameRenderer.pick(float)` and needs no Mixin
at all. From 26.1 onwards the same method lives on `Minecraft` and is private, so an `@Invoker` is
used to call it.

## Obsidian attack guard — L1 local input filter

While an End Crystal is held in the main hand, a client-side left click on obsidian is cancelled
before Vanilla starts a block break, so no `START_DESTROY_BLOCK` packet is produced. This suppresses
a packet the player did not intend rather than adding one, and it cannot break a block the player
could not otherwise break.

## What this mod does not do

- It does not change `rightClickDelay`, attack cooldown, reach, rotation, or any other rate or range
  limit.
- It does not place, break, target or aim at anything on the player's behalf.
- It does not send, drop, reorder, duplicate or rewrite any gameplay packet.
- It does not read server state the Vanilla client is not already given.

## What a packet-based anticheat sees

Gladiator, and every other server-side anticheat of that family, judges a player from the packet
stream. This is what that stream looks like with Crystal Tweaks installed.

**Unchanged, exactly Vanilla:**

| Signal | Status |
| --- | --- |
| Attack packet content, timing, order, count | Untouched. The mod runs after Vanilla handed it to the connection. |
| Yaw and pitch | Never written. The mod never rotates the player and never aims. |
| Reach | Never extended. Every packet targets what the player genuinely looks at. |
| Attack cooldown, `rightClickDelay`, click rate | Never modified. The same caps as an unmodded client. |
| Placement packets | Only observed, never created or altered. |
| Sequence numbers and acknowledgement | Vanilla's own prediction machinery, untouched. |

Aim, reach, timer, autoclicker and rotation checks therefore see an unmodified client, because the
values those checks read are produced entirely by Vanilla.

**The two places the mod does change the stream:**

1. *A suppressed packet.* While holding a crystal, left-clicking obsidian does not start a block
   break, so no `START_DESTROY_BLOCK` is sent. This removes an action the player did not intend. No
   anticheat penalises a player for not mining a block.

2. *An earlier placement packet.* After a predicted break the crosshair no longer rests on the dead
   crystal, so the next Use action sends `USE_ITEM_ON` at the obsidian rather than an
   `INTERACT_AT` at a crystal the server is about to destroy. This is the same packet Vanilla would
   send one round trip later, sent at the player's real rotation, within the player's real reach and
   at the player's real click rate.

Occasionally that placement reaches the server while the crystal is still alive there, and the
server refuses it. That outcome is indistinguishable from an ordinary high-ping player, who produces
it constantly in crystal PvP without any mod.

## Honest limits of this claim

- Crystal Tweaks removes a real client-side entity. That is a stronger intervention than a purely
  visual mod such as Client Side Crystals, which only draws an extra crystal and can never influence
  targeting. Anyone comparing the two should know the difference.
- The prediction makes a high-ping player behave like a zero-ping player. It does not make anyone
  faster than a zero-ping player, because no rate limit is touched. Heuristics tuned to "too fast
  for a human" are unaffected; heuristics tuned to "too fast for this player's measured latency"
  could in principle notice, and no such check is known to the author.
- Machine-learning crystal-aura detectors judge cadence and aim consistency. Neither is generated by
  this mod: the aim is the player's, and the cadence is bounded by Vanilla's own cooldowns.
- Client attestation is a separate matter. A launcher or cooperating client anticheat can see the
  installed JAR and its Mixins regardless of behavior, and some servers ban by mod list rather than
  by conduct.

## Server rules still apply

Server rules differ and this project cannot guarantee acceptance by every server or anticheat.
Review the rules of every multiplayer server and obtain staff approval when required. Where a server
forbids client-side prediction of any kind, this mod is not suitable for it.

## Verification evidence

- Every supported Minecraft target is compiled from its matching mapped API via
  `gradle/versions.properties` and `tools/build-all.ps1`.
- The monitor contains no networking or interaction calls.
- The placement hook is non-cancellable and observes the completed Vanilla send path.
- Built JAR hashes are published in `CHECKSUMS.sha256`.
- Runtime PvP verification remains necessary for tap/hold, main/offhand, GUI-open, high-ping and
  remapped-input scenarios; this build process does not launch game instances automatically.
