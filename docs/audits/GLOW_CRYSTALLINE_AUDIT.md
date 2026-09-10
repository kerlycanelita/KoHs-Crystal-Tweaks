# Crystal glow 300% and afterglow — scoped audit

## Pre-implementation verdict

Scope: the glow/paint features only, on Minecraft 1.21.11, 26.1, 26.1.2 and 26.2. Preserve the previous halo/reflection scale at 100%, extend both controls to 300%, add a tinted crystalline core/starburst and fade only its light after the crystal disappears.

Classification: **L0, local presentation**. The new code may read render states and removal/visibility state, submit geometry, and manage bounded local effect lifetimes. It must not retain or create a world entity, cancel removal, touch input, camera rotation, raycasts used for targeting, collision, inventory, cooldowns, or send/change/schedule packets.

Server before/after: the same gameplay path remains responsible for actions. This feature adds no action packets or custom payloads. A server-only anticheat cannot read these rendered pixels. A cooperative client/launcher can inspect installed files/mods; this is not an undetectability or universal server-approval claim. External optimizers retain responsibility for their own observable behavior.

Required evidence: inspect the exact render hooks, check external optimizer hook overlap, test 0/100/300 scaling and alpha bounds, test fade expiry/reset/repeated placement and config persistence, build four exact-target JARs, verify Mixin targets and metadata. Runtime/shader/combined-mod visual testing remains a user task because no game instance is to be launched.

Implementation constraints: visuals remain independent from the optimization guard; known other optimizers make the existing interaction core stand down. Afterglow is not an interactable crystal and must not delay or reverse another mod's removal. Chunk/world unloading must clear effects. All geometry retains depth testing.
