# Changelog - 1.21.5

## 2026-04-24
- Split del rango antiguo: se crea la carpeta `1.21.5` para aislar la build base de `1.21.5`.
- Error corregido: el bloque anterior mezclaba bytecode de `1.21.2` con subversiones donde `ModelPart` ya no usa `pivotY` y la transformacion del modelo ya no se aplica con `rotate(MatrixStack)`.
- Como se corrigio: `EndCrystalEntityModelAnimationMixin` ahora usa `originY` y `EndCrystalEntityRendererSeamlessMixin` usa `applyTransform(...)` para las partes hijas renderizadas con tint.
- Error corregido: `SafeCrystalMixin` podia terminar llamando a una firma inestable al leer el mundo desde el player en runtime.
- Como se corrigio: el mixin ahora toma `client.world`, evitando el `NoSuchMethodError` reportado en `1.21.5`.
- Error corregido: la config screen usa la ruta de `OrderedText` compatible con la base compilada de `1.21.5`.
- Se actualizo `fabric.mod.json` para declarar `>=1.21.5 <1.21.6`.
- Verificacion completada: `./gradlew.bat clean build` compila limpio.
