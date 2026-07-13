# KoHs Crystal Tweaks — Minecraft 26.1.2

## Matriz

- Mod: `1.1.0-beta.1`
- Minecraft: `~26.1.2`
- Java: 25
- Mappings: nombres oficiales de Mojang
- Fabric Loader: `0.19.3`
- Fabric API: `0.153.0+26.1.2`
- Artefacto: `kohs-crystal-tweaks-1.1.0-beta.1+mc26.1.2.jar`

## Implementación de esta beta

- Placement Fix integrado en `MultiPlayerGameMode.useItemOn` y activado por defecto.
- Confirmación en `Tweaks`: `Aceptar` desactiva y `Restablecer` conserva la función.
- Predicción local sólo tras `InteractionResult.Success`.
- Timeout visual inicial de 12 ticks con adaptación posterior.
- Tintes frame/core aplicados por pieza durante el consumo real de la cola de render.
- `Spin Speed`, `Crystal Flotation` y `Static Crystal` conectados al modelo y al beam.
- Pantalla responsiva con panel, tabs, botones y picker dentro de los límites lógicos.

Placement Fix modifica sólo el `BlockHitResult` de la interacción actual. No envía paquetes extra, no repite clics y no automatiza combate.

## Build verificado

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
.\gradlew.bat clean build --no-daemon
```

Estado: compilación exitosa el 2026-07-12. Minecraft no fue ejecutado.

