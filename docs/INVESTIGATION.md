# KoHs 2.0.4 safety investigation

## Why this review was required

Users reported multiplayer enforcement after installing earlier builds. A local code review cannot prove the exact cause of every enforcement event because servers use different rules and detection systems, but it can identify behavior that unnecessarily changes the normal client/server interaction boundary.

The review treated the following patterns as unacceptable for the 2.0.4 safety release:

- custom client-identification or compatibility messages sent when joining a server;
- multiple actions generated from one physical input;
- actions replayed, delayed, retried, or moved into a sub-tick queue;
- automatic hotbar selection;
- locally created entities that can become a crosshair target;
- attacks delayed until a future entity appears;
- guessed entity IDs or remote target selection;
- direct removal of vanilla cooldowns.

## Findings

1. Custom identification messages expose implementation details to a server without being required for normal Fabric gameplay.
2. Reordering or replaying inputs can make the outgoing action sequence differ from an unmodified client even when each individual action originated from the player.
3. Delaying an attack until an entity later appears changes the time at which the action occurs and can turn one old input into a future action.
4. Automatic slot changes alter player state independently of the physical hotbar selection performed by the user.
5. A preview implemented as a normal local entity can enter raycasting and leave a stale entity reference in the crosshair target. A movement-only `noClip` flag does not make an entity non-targetable.
6. Local removal of an unconfirmed or predicted entity can make the visual world disagree with the server and can affect subsequent targeting.
7. A purely visual preview does not need an entity ID, UUID, bounding box, collision state, or registration in the client world.

## 2.0.4 decisions

- Remove every custom identification, version, opt-out, and acknowledgement message.
- Keep Minecraft's normal input and interaction pipeline responsible for combat actions.
- Enforce this invariant:

```text
one physical attack/use input -> zero or one corresponding vanilla action
```

- Do not replay consumed input.
- Do not defer an action until a placement or entity appears.
- Do not create an automatic retry.
- Do not select or restore a hotbar slot on behalf of the player.
- Represent an optional placement preview as render-only state. It must not be registered as an entity and must never participate in raycasting.
- Keep the visual preview OFF on a fresh installation.
- Allow optional local cleanup only for the exact real crystal supplied by the server and selected by the player's normal vanilla attack.
- Keep confirmed local cleanup OFF on a fresh installation.
- Clear temporary visual state on timeout, disconnect, world change, configuration disable, and client shutdown.

## Network boundary

KoHs 2.0.4 does not need a custom networking channel for its client-only configuration and rendering features. It does not construct an additional combat action packet.

The normal Minecraft interaction path may send the usual vanilla action caused by the player's physical input. The server remains authoritative and can accept or reject that action.

## Verification requirements

Each maintained version should pass the following checks before publication:

1. Search the source and resources for custom identification-channel registration.
2. Search for direct combat-packet construction or transmission.
3. Verify that no held-input loop, replay queue, delayed intent, or automatic retry remains.
4. Verify that no gameplay feature changes the selected hotbar slot automatically.
5. Verify that the optional preview cannot produce an `EntityHitResult`.
6. Verify that local cleanup requires an exact server-provided entity and a normal player attack.
7. Compare outgoing action counts with features OFF and ON for the same physical input sequence.
8. Run clean Gradle builds and automated tests for every maintained Minecraft target.

Minecraft is not launched during automated release preparation; runtime testing remains a separate manual step.

## Scope and server rules

These changes reduce avoidable differences from vanilla input and networking. They do not make the mod universally permitted or undetectable. A server may prohibit visual previews, local cleanup, or all gameplay-related client mods regardless of packet behavior.

Users are responsible for checking server rules. When a rule is unclear, the safest configuration is to leave optional gameplay-related features disabled or remove the mod for that server.
