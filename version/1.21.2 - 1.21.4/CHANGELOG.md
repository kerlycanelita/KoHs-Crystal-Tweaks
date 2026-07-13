# Changelog - 1.21.2 - 1.21.4

## 2026-04-24
- Split del rango antiguo: `1.21.2 - 1.21.9` se dividio para que este bloque cubra solo `1.21.2 - 1.21.4`.
- Motivo: el rango viejo anunciaba compatibilidad mas alla de la API real y podia romper en runtime en subversiones posteriores.
- Se mantuvo esta carpeta sobre la base `1.21.2`, que sigue compilando limpio y coincide con el renderer y API validos hasta `1.21.4`.
- Se actualizo `fabric.mod.json` para declarar `>=1.21.2 <1.21.5`.
- Se actualizaron README y metadatos para reflejar el rango real.
- Verificacion completada: `./gradlew.bat clean build` compila limpio.
