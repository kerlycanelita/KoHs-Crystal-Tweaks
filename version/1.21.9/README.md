# Estado de compatibilidad - Minecraft 1.21.9

## Informacion general
- Mod: KoHs Crystal Tweaks
- Version objetivo: 1.21.9
- Carpeta: `version/1.21.9`
- Compatibilidad declarada en `fabric.mod.json`: `>=1.21.9 <1.21.10`
- Artefacto esperado: `kohs-crystal-tweaks-1.0.0+mc1.21.9`

## Estado actual
- Estado: Verificado y compilando
- Compilacion: `./gradlew.bat clean build` exitosa el 2026-04-24
- Mappings: `1.21.9+build.1`
- Loader: `0.17.2`
- Fabric API: `0.134.1+1.21.9`

## Cambios validados
- `1.21.9` ya usa el pipeline moderno de renderer y por eso queda aislado en su propia carpeta.
- El renderer seamless sigue adaptado al renderer moderno con `OrderedRenderCommandQueue` y `EndCrystalEntityRenderState`.
- El custom sound usa la misma ruta vanilla del juego para reemplazar `minecraft:entity.generic.explode`, sin delay extra por reproduccion paralela.
- Se retiro `Anchor Charge`; esta compilacion solo conserva el override del sonido de explosion.
- La proteccion contra blur vanilla en la pantalla de configuracion se mantiene activa.
- `Client Crystal` y el optimizador siguen siendo legit del lado del cliente: no automatizan ataques ni crean paquetes de combate propios.
- Se fusiono la parte util de `Marlow's Crystal Optimizer`: handshake moderno `marlowcrystal:version`, `marlowcrystal:opt_out` y `marlowcrystal:opt_out_ack`, cache por servidor y apagado automatico del optimizador si el servidor pide opt-out.
- El hook de optimizacion solo escucha el `PlayerInteractEntityC2SPacket` vanilla que ya ibas a mandar y hace limpieza visual local del crystal; no inyecta golpes extra.

## Checklist
- [x] Revisar `gradle.properties`
- [x] Revisar `fabric.mod.json`
- [x] Ejecutar compilacion limpia
- [x] Separar `1.21.9` del rango anterior para evitar mezclar APIs internas incompatibles
- [x] Corregir mixins del renderer y audio
- [x] Confirmar config screen sin blur vanilla no deseado
- [x] Documentar estado final
