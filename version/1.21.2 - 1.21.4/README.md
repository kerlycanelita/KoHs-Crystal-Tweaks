# Estado de compatibilidad - Minecraft 1.21.2 - 1.21.4

## Informacion general
- Mod: KoHs Crystal Tweaks
- Rango objetivo: 1.21.2 a 1.21.4
- Carpeta: `version/1.21.2 - 1.21.4`
- Base de build: `minecraft_version=1.21.2`
- Compatibilidad declarada en `fabric.mod.json`: `>=1.21.2 <1.21.5`
- Artefacto base esperado: `kohs-crystal-tweaks-1.0.0+mc1.21.2`

## Estado actual
- Estado: Separado desde el merge amplio y verificado
- Compilacion: `./gradlew.bat clean build` exitosa el 2026-04-24
- Mappings: `1.21.2+build.1`
- Loader: `0.17.2`
- Fabric API: `0.106.1+1.21.2`

## Cambios validados
- Este bloque conserva la base vieja de renderer que sigue siendo valida hasta `1.21.4`.
- El custom sound reemplaza `minecraft:entity.generic.explode` desde `SoundManager` y `SoundLoader`, sin el delay del sistema manual anterior.
- Se retiro `Anchor Charge`; este rango conserva solo el reemplazo del sonido de explosion vanilla.
- La pantalla de configuracion mantiene un fondo manual para que el blur vanilla no invada el menu del mod.
- El rango se recorto para evitar incompatibilidades binarias al anunciar soporte mas alla de `1.21.4`.

## Checklist
- [x] Revisar `gradle.properties`
- [x] Revisar `fabric.mod.json`
- [x] Ejecutar compilacion limpia
- [x] Validar que el bloque siga estable en la API vieja del renderer
- [x] Reacotar el rango declarado al soporte real
- [x] Confirmar aislamiento del config screen frente al blur vanilla
- [x] Documentar estado final
