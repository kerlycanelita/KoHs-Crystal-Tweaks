# Crystal Tweaks KoHs 2.2.8 — changelogs para Modrinth

Una version de Modrinth por version de Minecraft, formato `2.2.8+mc<version>`,
canal Release, loader Fabric, entorno solo cliente.

Dependencias en la pantalla emergente, iguales en las cinco:
**Fabric API `required`** y **Mod Menu `optional`**.

---

## `2.2.8+mc1.21.11`

Archivo: `crystal-tweaks-1.21.11-2.2.8.jar`  
Fabric API: `0.141.6+1.21.11`  ·  Mod Menu: `17.0.0`

<!-- pegar desde aqui -->

### Fixed

**Placing right after a break no longer gets discarded.**
Until now the mod removed the crystal you hit from the client. That changed what your crosshair was
on, so the next placement could take Vanilla's entity branch, do nothing, and still spend its
four-tick cooldown: about 200 ms lost with nothing shown. It was worst on a *fast* connection,
because more server-confirmed crystals are on screen for the ray to land on, and worst again with
Attack and Use on separate keys, since both can fire in the same tick.

**No more invisible crystals blocking a spot.**
When the server refused a hit, the crystal stayed on the server while your client had already thrown
it away, and every later placement on that obsidian was silently rejected.

Both come from the same cause, and it is gone: the crystal is now only skipped while drawing. The
entity, the crosshair, the interaction ranges, the cooldowns and the outgoing packets are exactly
what an unmodified client produces, and a prediction the server refuses simply becomes visible again.

### Changed

How long a crystal stays hidden now follows the round trip the mod measures for itself. The previous
fixed window was wrong at both ends: on a quick connection it kept a refused hit hidden long after
the server had answered, and on a slow one it expired mid-flight, so the crystal reappeared and then
vanished again when the confirmation landed.

### Note

Ghost crystals are not available on this Minecraft version. The renderer needs level render events
that only exist from 26.1 onwards, so the option is not shown here rather than offered as a toggle
that would do nothing. Everything else in this release applies.

### Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.4 or newer
- Java 21
- Fabric API 0.141.6+1.21.11
- Mod Menu 17.0.0 or newer (optional)

### Multiplayer

No gameplay packet is fabricated, delayed, reordered or duplicated, and reach, rotation, attack
cooldown and click rate are untouched. The full client/server boundary is documented in
`docs/LEGITIMACY_AUDIT.md`. Server rules differ; check yours.

**SHA-256**
```
cc100d24c66cf99ad47ebeeec972f21861910fc53013264b5c0eacdf010ea40b  crystal-tweaks-1.21.11-2.2.8.jar
```

<!-- hasta aqui -->

---

## `2.2.8+mc26.1`

Archivo: `crystal-tweaks-26.1-2.2.8.jar`  
Fabric API: `0.145.1+26.1`  ·  Mod Menu: `18.0.0`

<!-- pegar desde aqui -->

### Fixed

**Placing right after a break no longer gets discarded.**
Until now the mod removed the crystal you hit from the client. That changed what your crosshair was
on, so the next placement could take Vanilla's entity branch, do nothing, and still spend its
four-tick cooldown: about 200 ms lost with nothing shown. It was worst on a *fast* connection,
because more server-confirmed crystals are on screen for the ray to land on, and worst again with
Attack and Use on separate keys, since both can fire in the same tick.

**No more invisible crystals blocking a spot.**
When the server refused a hit, the crystal stayed on the server while your client had already thrown
it away, and every later placement on that obsidian was silently rejected.

Both come from the same cause, and it is gone: the crystal is now only skipped while drawing. The
entity, the crosshair, the interaction ranges, the cooldowns and the outgoing packets are exactly
what an unmodified client produces, and a prediction the server refuses simply becomes visible again.

### Changed

How long a crystal stays hidden now follows the round trip the mod measures for itself. The previous
fixed window was wrong at both ends: on a quick connection it kept a refused hit hidden long after
the server had answered, and on a slow one it expired mid-flight, so the crystal reappeared and then
vanished again when the confirmation landed.

### Added

**Ghost crystals — optional, off by default.**
Draws a stand-in crystal while the server's real one is still in flight, so placing does not depend
on your ping. Turn it on under **Advanced Tweaks**.

It is purely visual. The stand-in is never added to the world, so Vanilla cannot see it: it is not
pickable, has no collision, appears in no entity query, and no packet can ever refer to it. When the
server's crystal arrives the stand-in is dropped and the real entity is what stays on screen.

It ships off because it changes what the world looks like rather than only how quickly it catches
up, and that is the player's call to make.

### Requirements

- Minecraft 26.1
- Fabric Loader 0.18.6 or newer
- Java 25
- Fabric API 0.145.1+26.1
- Mod Menu 18.0.0 or newer (optional)

### Multiplayer

No gameplay packet is fabricated, delayed, reordered or duplicated, and reach, rotation, attack
cooldown and click rate are untouched. The full client/server boundary is documented in
`docs/LEGITIMACY_AUDIT.md`. Server rules differ; check yours.

**SHA-256**
```
0bdee7026491412ebb023b811a7940235a75e99583a7ae63a7e4db30097c626c  crystal-tweaks-26.1-2.2.8.jar
```

<!-- hasta aqui -->

---

## `2.2.8+mc26.1.1`

Archivo: `crystal-tweaks-26.1.1-2.2.8.jar`  
Fabric API: `0.145.4+26.1.1`  ·  Mod Menu: `18.0.0`

<!-- pegar desde aqui -->

### Fixed

**Placing right after a break no longer gets discarded.**
Until now the mod removed the crystal you hit from the client. That changed what your crosshair was
on, so the next placement could take Vanilla's entity branch, do nothing, and still spend its
four-tick cooldown: about 200 ms lost with nothing shown. It was worst on a *fast* connection,
because more server-confirmed crystals are on screen for the ray to land on, and worst again with
Attack and Use on separate keys, since both can fire in the same tick.

**No more invisible crystals blocking a spot.**
When the server refused a hit, the crystal stayed on the server while your client had already thrown
it away, and every later placement on that obsidian was silently rejected.

Both come from the same cause, and it is gone: the crystal is now only skipped while drawing. The
entity, the crosshair, the interaction ranges, the cooldowns and the outgoing packets are exactly
what an unmodified client produces, and a prediction the server refuses simply becomes visible again.

### Changed

How long a crystal stays hidden now follows the round trip the mod measures for itself. The previous
fixed window was wrong at both ends: on a quick connection it kept a refused hit hidden long after
the server had answered, and on a slow one it expired mid-flight, so the crystal reappeared and then
vanished again when the confirmation landed.

### Added

**Ghost crystals — optional, off by default.**
Draws a stand-in crystal while the server's real one is still in flight, so placing does not depend
on your ping. Turn it on under **Advanced Tweaks**.

It is purely visual. The stand-in is never added to the world, so Vanilla cannot see it: it is not
pickable, has no collision, appears in no entity query, and no packet can ever refer to it. When the
server's crystal arrives the stand-in is dropped and the real entity is what stays on screen.

It ships off because it changes what the world looks like rather than only how quickly it catches
up, and that is the player's call to make.

### Requirements

- Minecraft 26.1.1
- Fabric Loader 0.18.6 or newer
- Java 25
- Fabric API 0.145.4+26.1.1
- Mod Menu 18.0.0 or newer (optional)

### Multiplayer

No gameplay packet is fabricated, delayed, reordered or duplicated, and reach, rotation, attack
cooldown and click rate are untouched. The full client/server boundary is documented in
`docs/LEGITIMACY_AUDIT.md`. Server rules differ; check yours.

**SHA-256**
```
bbee2c70c1755acc58079d410addd0fa5aeb3a2234539e696a0b20c0cb3a8087  crystal-tweaks-26.1.1-2.2.8.jar
```

<!-- hasta aqui -->

---

## `2.2.8+mc26.1.2`

Archivo: `crystal-tweaks-26.1.2-2.2.8.jar`  
Fabric API: `0.155.2+26.1.2`  ·  Mod Menu: `18.0.0`

<!-- pegar desde aqui -->

### Fixed

**Placing right after a break no longer gets discarded.**
Until now the mod removed the crystal you hit from the client. That changed what your crosshair was
on, so the next placement could take Vanilla's entity branch, do nothing, and still spend its
four-tick cooldown: about 200 ms lost with nothing shown. It was worst on a *fast* connection,
because more server-confirmed crystals are on screen for the ray to land on, and worst again with
Attack and Use on separate keys, since both can fire in the same tick.

**No more invisible crystals blocking a spot.**
When the server refused a hit, the crystal stayed on the server while your client had already thrown
it away, and every later placement on that obsidian was silently rejected.

Both come from the same cause, and it is gone: the crystal is now only skipped while drawing. The
entity, the crosshair, the interaction ranges, the cooldowns and the outgoing packets are exactly
what an unmodified client produces, and a prediction the server refuses simply becomes visible again.

### Changed

How long a crystal stays hidden now follows the round trip the mod measures for itself. The previous
fixed window was wrong at both ends: on a quick connection it kept a refused hit hidden long after
the server had answered, and on a slow one it expired mid-flight, so the crystal reappeared and then
vanished again when the confirmation landed.

### Added

**Ghost crystals — optional, off by default.**
Draws a stand-in crystal while the server's real one is still in flight, so placing does not depend
on your ping. Turn it on under **Advanced Tweaks**.

It is purely visual. The stand-in is never added to the world, so Vanilla cannot see it: it is not
pickable, has no collision, appears in no entity query, and no packet can ever refer to it. When the
server's crystal arrives the stand-in is dropped and the real entity is what stays on screen.

It ships off because it changes what the world looks like rather than only how quickly it catches
up, and that is the player's call to make.

### Requirements

- Minecraft 26.1.2
- Fabric Loader 0.18.6 or newer
- Java 25
- Fabric API 0.155.2+26.1.2
- Mod Menu 18.0.0 or newer (optional)

### Multiplayer

No gameplay packet is fabricated, delayed, reordered or duplicated, and reach, rotation, attack
cooldown and click rate are untouched. The full client/server boundary is documented in
`docs/LEGITIMACY_AUDIT.md`. Server rules differ; check yours.

**SHA-256**
```
35857fdcc93bba82b9b6c9b5b0c7a468a3e8c79ed6b822405c2ad60947d2a6c0  crystal-tweaks-26.1.2-2.2.8.jar
```

<!-- hasta aqui -->

---

## `2.2.8+mc26.2`

Archivo: `crystal-tweaks-26.2-2.2.8.jar`  
Fabric API: `0.156.0+26.2`  ·  Mod Menu: `20.0.1`

<!-- pegar desde aqui -->

### Fixed

**Placing right after a break no longer gets discarded.**
Until now the mod removed the crystal you hit from the client. That changed what your crosshair was
on, so the next placement could take Vanilla's entity branch, do nothing, and still spend its
four-tick cooldown: about 200 ms lost with nothing shown. It was worst on a *fast* connection,
because more server-confirmed crystals are on screen for the ray to land on, and worst again with
Attack and Use on separate keys, since both can fire in the same tick.

**No more invisible crystals blocking a spot.**
When the server refused a hit, the crystal stayed on the server while your client had already thrown
it away, and every later placement on that obsidian was silently rejected.

Both come from the same cause, and it is gone: the crystal is now only skipped while drawing. The
entity, the crosshair, the interaction ranges, the cooldowns and the outgoing packets are exactly
what an unmodified client produces, and a prediction the server refuses simply becomes visible again.

### Changed

How long a crystal stays hidden now follows the round trip the mod measures for itself. The previous
fixed window was wrong at both ends: on a quick connection it kept a refused hit hidden long after
the server had answered, and on a slow one it expired mid-flight, so the crystal reappeared and then
vanished again when the confirmation landed.

### Added

**Ghost crystals — optional, off by default.**
Draws a stand-in crystal while the server's real one is still in flight, so placing does not depend
on your ping. Turn it on under **Advanced Tweaks**.

It is purely visual. The stand-in is never added to the world, so Vanilla cannot see it: it is not
pickable, has no collision, appears in no entity query, and no packet can ever refer to it. When the
server's crystal arrives the stand-in is dropped and the real entity is what stays on screen.

It ships off because it changes what the world looks like rather than only how quickly it catches
up, and that is the player's call to make.

### Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Java 25
- Fabric API 0.156.0+26.2
- Mod Menu 20.0.1 or newer (optional)

### Multiplayer

No gameplay packet is fabricated, delayed, reordered or duplicated, and reach, rotation, attack
cooldown and click rate are untouched. The full client/server boundary is documented in
`docs/LEGITIMACY_AUDIT.md`. Server rules differ; check yours.

**SHA-256**
```
70146084d415755c1eb8f60315b49937cb12eaea9eb20afec177741aef6a7448  crystal-tweaks-26.2-2.2.8.jar
```

<!-- hasta aqui -->

---

