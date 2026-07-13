# Changelog - 1.21.6 - 1.21.8

## 2026-04-24
- Split del rango anterior: se crea la carpeta `1.21.6 - 1.21.8` porque `1.21.6` ya no comparte compatibilidad binaria real con la build base de `1.21.5`.
- Error corregido: la build antigua `1.21.5 - 1.21.8` podia crashear en `1.21.6` por `NoSuchMethodError` dentro de `KoHsCrystalTweaksConfigScreen` al abrir el menu del mod.
- Como se corrigio: este bloque se recompila contra `minecraft_version=1.21.6` y reacota su metadata a `>=1.21.6 <1.21.9` para alinear bytecode y rango declarado.
- Verificacion completada: `./gradlew.bat clean build` compila limpio en esta carpeta.
