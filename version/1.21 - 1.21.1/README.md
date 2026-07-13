# Estado de compatibilidad - Minecraft 1.21 - 1.21.1

## Informacion general
- Mod: KoHs Crystal Tweaks
- Rango objetivo: 1.21 a 1.21.1
- Carpeta: `version/1.21 - 1.21.1`
- Compatibilidad declarada en `fabric.mod.json`: `>=1.21 <1.21.2`
- Artefacto base esperado: `kohs-crystal-tweaks-1.0.0+mc1.21`

## Estado actual
- Estado: Fusionado, verificado y estable
- Compilacion: `./gradlew.bat clean build` exitosa el 2026-04-23
- Mappings: `1.21+build.9`
- Loader: `0.18.6`
- Fabric API: `0.102.0+1.21`

## Cambios validados
- El custom sound ya no usa reproduccion paralela manual: ahora reemplaza `minecraft:entity.generic.explode` dentro de la tuberia vanilla de sonido, con el mismo timing del juego.
- Se retiró `Anchor Charge`; esta rama ya solo gestiona el reemplazo del sonido de explosion vanilla.
- La importacion de audio mantiene soporte para `WAV`, `OGG` y `MP3`.
- La pantalla de configuracion sigue aislada del blur vanilla y conserva su fondo propio.

## Checklist
- [x] Revisar `gradle.properties`
- [x] Revisar `fabric.mod.json`
- [x] Ejecutar compilacion limpia
- [x] Validar mixins y override runtime de sonido
- [x] Confirmar compatibilidad del rango fusionado
- [x] Documentar estado final
