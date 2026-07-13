# KoHs Crystal Tweaks — Minecraft 1.21.11

## Matriz

- Mod: `1.1.0-beta.1`
- Minecraft: `>=1.21.11`
- Java: 21
- Yarn: `1.21.11+build.5`
- Fabric Loader: `0.17.2`
- Fabric API: `0.140.0+1.21.11`
- Artefacto: `kohs-crystal-tweaks-1.1.0-beta.1+mc1.21.11.jar`

## Implementación de esta beta

- Placement Fix integrado en `ClientPlayerInteractionManager.interactBlock` y activado por defecto.
- Confirmación en `Tweaks`: `Aceptar` desactiva y `Restablecer` conserva la función.
- Predicción local sólo tras `ActionResult.isAccepted()`.
- Base válida igual a vanilla: obsidiana o bedrock.
- Timeout visual inicial de 12 ticks con adaptación posterior.
- Tint outer/core aplicado por identidad en `ModelPart.render`, compatible con el renderer por cola.
- Layout limitado a las dimensiones lógicas de pantalla.

Placement Fix modifica sólo el `BlockHitResult` de la interacción actual. No envía paquetes extra, no repite clics y no automatiza combate.

## Build verificado

```powershell
.\gradlew.bat clean build --no-daemon
```

Estado: compilación exitosa el 2026-07-12. Minecraft no fue ejecutado.

