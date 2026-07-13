# Estado de compatibilidad - Minecraft 1.21.6 - 1.21.8

## Informacion general
- Mod: KoHs Crystal Tweaks
- Rango objetivo: 1.21.6 a 1.21.8
- Carpeta: `version/1.21.6 - 1.21.8`
- Base de build: `minecraft_version=1.21.6`
- Compatibilidad declarada en `fabric.mod.json`: `>=1.21.6 <1.21.9`
- Artefacto base esperado: `kohs-crystal-tweaks-1.0.0+mc1.21.6`

## Estado actual
- Estado: Version intermedia separada por cambios reales de API
- Compilacion: `./gradlew.bat clean build` exitosa el 2026-04-24 tras recompilar contra `1.21.6`
- Mappings: `1.21.6+build.1`
- Loader: `0.19.2`
- Fabric API: `0.128.2+1.21.6`

## Cambios validados
- Este bloque usa la API intermedia de `1.21.6+`, separada de `1.21.5` por cambios binarios reales en cliente.
- El custom sound reemplaza `minecraft:entity.generic.explode` desde `SoundManager` y `SoundLoader`, sin el delay del sistema manual anterior.
- Se retiro `Anchor Charge`; este rango conserva solo el reemplazo del sonido de explosion vanilla.
- La pantalla de configuracion mantiene un fondo manual para que el blur vanilla no invada el menu del mod.
- `SafeCrystalMixin` ya no depende de una llamada binariamente fragil al leer el mundo del player.

## Checklist
- [x] Revisar `gradle.properties`
- [x] Revisar `fabric.mod.json`
- [x] Ejecutar compilacion limpia
- [x] Adaptar mixins a la API de `1.21.6 - 1.21.8`
- [x] Reacotar el rango declarado al soporte real
- [x] Confirmar aislamiento del config screen frente al blur vanilla
- [x] Documentar estado final
