# Changelog - 1.21 - 1.21.1

## 2026-04-23
- Fusion validada: la base compartida para `1.21` y `1.21.1` se mantiene en una sola carpeta (`1.21 - 1.21.1`).
- Verificacion completada: `./gradlew.bat clean build` compila correctamente sin errores de mixin ni de remapeo.
- Rework de audio vanilla: el reemplazo ahora se hace como `resource pack runtime`, inyectando un `Sound` virtual dentro de `SoundManager` y `SoundLoader` en vez de reproducir OpenAL manualmente.
- Rework de delay: al usar la misma tuberia de sonido de Minecraft se elimina el desfase agregado por la reproduccion paralela y el custom sound entra con el mismo timing que el sonido vanilla.
- Rework cliente/servidor: el override ya no depende de detectar muertes de crystal o posiciones de explosion; ahora cualquier reproduccion del evento vanilla de explosion usa el sonido custom cargado.
- Ajuste de configuracion: se retiró la opcion `Anchor Charge` para dejar el sistema enfocado solo en el reemplazo del sonido de explosion.
- Limpieza del merge: las carpetas individuales residuales se eliminaron del arbol `version/` para dejar solo la carpeta fusionada activa.
- Menu de configuracion: se mantuvo la logica existente del config screen sin introducir blur vanilla no deseado.
