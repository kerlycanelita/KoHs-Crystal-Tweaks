# 2.2.10 release review

## Scope and pre-change verdict

Review of commit `51f832e` against the published 2.2.9 artifacts, followed by a
compatibility hotfix for Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2 and 26.2.
The 26.1.2 binary comparison found changes only in placement observation,
optimizer detection, attack prediction, the optimizer guard and Mixin priority.
Glow, colors, other-player profiles, sound and GUI code were identical.

The proposed additional fix is **L0 local state**: recheck the optimizer guard
when a queued attack prediction runs, and serialize conflict shutdown with local
prediction updates on Minecraft's client thread. Clear placement latency state
without clearing ownership colors, and stop taking new latency samples after
shutdown. These operations do not invoke a gameplay action or change a packet.

Input still follows Vanilla logical Attack/Use mappings. The attack observer is
at Connection.send HEAD (not TAIL as an older comment claims); the placement
observer is at TAIL. Neither observer cancels or rewrites the send. Rendering
reads hide markers; the actual entity and crosshair remain unchanged.

A server-only anticheat cannot inspect local hide markers, colors or the scan
result. A cooperating client/launcher can inspect the installed mod and classes.
No packet timing trace was recorded, so exact wall-clock timing equivalence is
not claimed. No direct send, retry, cooldown, range, rotation or input-rate
change is part of this hotfix.

The existing Safe Crystal input filter remains separate: while active, it
suppresses starting an obsidian break with a crystal held. This changes the
action packet count relative to Vanilla and is **L2** under the audit taxonomy;
it must not be described as universally packet-equivalent or anticheat-approved.
Detecting another optimizer disables this filter as before. Its behavior is not
expanded by this release.

## Findings

- The new early attack guard avoids unnecessary queued work, but removing the
  second guard lets an already queued prediction run after a conflict is found.
- Concurrent-map reset alone is insufficient: a client task can add another
  marker after the background scan clears the maps.
- Placement confirmation still samples latency after shutdown, and pending
  placement samples are not cleared by the conflict detector.
- The publication helper compares a version number with display names, omits
  dependencies and references a missing changelog. It is unsuitable for this
  release without correction.

## Verification plan

The user's follow-up requires every interaction helper to remain disabled with
another optimizer. The guard now starts in CHECKING, permits work only in READY,
and blocks work in CONFLICT or FAILED. A clean scan alone enables it; late scan
completion cannot override a conflict or failure. Partial scanner failures also
leave it disabled. Prediction storage, ghost storage/rendering, placement
confirmation and tick bookkeeping use this gate. Ownership colors and other
visual/audio features do not use it. Runtime resets never clear the conflict.

This conservatively handles detected IDs, names and declared Mixin overlap;
arbitrary undeclared runtime code cannot be exhaustively identified by this
static scanner. It does not disable, overwrite or remove another mod's mixins.

Compile all five exact targets; run existing appearance/glow tests; inspect
Mixin targets against each target's cached Minecraft classes; verify metadata,
hashes and the rechecked guard in both mapping families. Publish a new patch
version with English release notes and Fabric API/Mod Menu dependencies.
No Minecraft instance will be launched, following the user's standing request.

## Results

- Rebuilt all five targets after the final compatibility changes; all builds succeeded.
- Appearance/glow tests passed, including 42,625 layouts and the guard's initial,
  clean, conflicting, failed and late-completion transitions.
- JAR dependency/metadata verification: 5 checked, 0 problems.
- Exact-version Mixin class/member verification: 5 checked, 0 problems, covering
  intermediary 1.21.11 and official 26.x namespaces.
- The queued-prediction guard and placement reset were inspected in compiled
  release bytecode for all five targets.
- Five listed releases were created with exact Minecraft tags, required Fabric
  API and optional Mod Menu version dependencies. Their downloadable CDN bytes
  match the local JARs and the API SHA-512 values.
- The project description and all release notes are English; the existing
  screenshot is preserved. Local SHA-256 values are in `CHECKSUMS.sha256`.
- No Minecraft instance was launched. Runtime combinations with external
  optimizers and packet-timing equivalence were not experimentally tested.

| Minecraft | Modrinth version ID |
| --- | --- |
| 1.21.11 | JJLbjUXP |
| 26.1 | f2pCtPRZ |
| 26.1.1 | AefTIADi |
| 26.1.2 | qEiFXaQk |
| 26.2 | cAWWKdU1 |
