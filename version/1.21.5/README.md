# Estado de compatibilidad - Minecraft 1.21.5

## Informacion general
- Mod: KoHs Crystal Tweaks
- Rango objetivo: 1.21.5
- Carpeta: `version/1.21.5`
- Base de build: `minecraft_version=1.21.5`
- Compatibilidad declarada en `fabric.mod.json`: `>=1.21.5 <1.21.6`
- Artefacto base esperado: `kohs-crystal-tweaks-1.0.0+mc1.21.5`

## Estado actual
- Estado: Version individual separada y verificada
- Compilacion: `./gradlew.bat clean build` exitosa el 2026-04-24
- Mappings: `1.21.5+build.1`
- Loader: `0.17.2`
- Fabric API: `0.128.2+1.21.5`

## Cambios validados
- Esta version usa la base `1.21.5` de `ModelPart`, con `originY` y `applyTransform(...)`.
- El custom sound reemplaza `minecraft:entity.generic.explode` desde `SoundManager` y `SoundLoader`, sin el delay del sistema manual anterior.
- Se retiro `Anchor Charge`; este rango conserva solo el reemplazo del sonido de explosion vanilla.
- La pantalla de configuracion mantiene un fondo manual para que el blur vanilla no invada el menu del mod.
- `SafeCrystalMixin` ya no depende de una llamada binariamente fragil y evita el crash reportado en `1.21.5`.

## Checklist
- [x] Revisar `gradle.properties`
- [x] Revisar `fabric.mod.json`
- [x] Ejecutar compilacion limpia
- [x] Adaptar mixins a la API de `1.21.5`
- [x] Reacotar el rango declarado al soporte real
- [x] Confirmar aislamiento del config screen frente al blur vanilla
- [x] Documentar estado final
