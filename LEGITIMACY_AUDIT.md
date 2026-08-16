# Client/Server Legitimacy Audit

This audit covers Crystal Tweaks 2.2.5 for Minecraft 1.21 through 26.2. It separates the newly added local functionality from behavior that already existed in the core.

## Trust model

- A server-only anticheat can inspect gameplay packets, their payloads, order and timing, but cannot read this local GUI, its scan results, particles, textures, sounds, configuration files or physical input devices.
- A mandatory launcher or cooperating client anticheat can inspect installed mod files, hashes and Mixin declarations within the permissions granted by the player.

## Conflict Monitor — L0 local-only

The monitor reads installed Fabric metadata, Mixin configuration JSON and class annotations from the local filesystem. It compares only normalized target classes and methods, then renders a local, non-blocking screen.

It does not send a custom payload, advertise scan results, modify a Mixin at runtime, change input, or invoke a gameplay action. Dynamic Mixin plugins and external bytecode agents may not be visible, so a clean result is not proof of universal compatibility.

## Placement observer and feedback — L0 local-only

Path:

```text
physical input → vanilla logical Use action → vanilla interaction packet
→ Connection.send(Packet) completes → local observer → local tracker
```

The observer runs at the tail of the existing Vanilla send method. Across the supported builds it only reads a genuine use-on-block packet after the Vanilla send path and records bounded local state. It never creates, sends, cancels, retries, duplicates or mutates a packet.

The following remain invariant:

- physical and remapped logical input handling;
- packet type, payload, count, order and sequence number;
- use cooldown, reach, rotation and selected inventory slot;
- server acknowledgement and authoritative crystal creation.

A server-only anticheat receives no additional evidence from this observer. An attested client can identify the installed mod or inspect its Mixin.

## Existing core behavior — L2 server-observable potential

The pre-existing attack optimizer removes an attacked crystal from the local world immediately and refreshes the local crosshair target before server confirmation. The attack packet already being sent is not changed, but this local prediction can affect the target or timing of a later player action. The obsidian attack guard can also suppress an accidental client block-attack action.

Those paths can therefore change later server-observable behavior and are classified L2. Version 2.2.5 does not expand them. The project must not be described as universally approved by anticheats; server rules and client-attestation policies still apply.

## Verification evidence

- Every supported Minecraft target is compiled from its matching mapped API.
- The new monitor contains no networking or interaction calls.
- The placement hook is non-cancellable and observes the completed Vanilla send path.
- Built JAR hashes are published in `CHECKSUMS.sha256`.
- Runtime PvP verification remains necessary for tap/hold, main/offhand, GUI-open, high-ping and remapped-input scenarios; this build process does not launch game instances automatically.
