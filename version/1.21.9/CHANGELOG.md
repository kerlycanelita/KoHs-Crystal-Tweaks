# Changelog - 1.21.9

## 2026-04-24
- Split del rango antiguo: `1.21.9` sale del merge amplio y queda aislado en su propia carpeta.
- Motivo: `1.21.9` ya usa el renderer moderno y la UI moderna, asi que no comparte la misma base fuente que `1.21.5 - 1.21.8`.
- Como se corrigio: se tomo como base la implementacion moderna ya validada en `1.21.10` y se ajustaron los metadatos para `1.21.9`.
- Mejora del optimizador legit: se fusiona la capa util de `Marlow's Crystal Optimizer` con handshake moderno `marlowcrystal:version`, `marlowcrystal:opt_out` y `marlowcrystal:opt_out_ack`.
- Como se corrigio: se reemplazo el canal legacy `minecraft:mco` por el protocolo moderno de Marlow, se agrego cache por servidor para respetar opt-out persistente y se mostro un aviso local cuando un servidor desactiva el optimizador.
- Mejora del handler cliente: el rompido local del crystal ahora reutiliza la logica de dano de Marlow para manejar mejor casos con `Weakness`, sin mandar paquetes extra ni automatizar combate.
- Ajuste legit: se restaura el `OptOutPacket` como una señal de compatibilidad pasiva en join, sin automatizar combate ni enviar acciones extras del crystal.
- Endurecimiento adicional: `SafeCrystalMixin` ya no depende de `player.getEntityWorld()`, evitando otra llamada binariamente fragil en cliente.
- Error corregido: el `@Inject` del renderer moderno usaba `Object` para `CameraRenderState`, lo que podia romper la aplicacion del mixin al iniciar con `InvalidInjectionException`.
- Como se corrigio: `kct$cancelHiddenCrystalRender` ahora declara el cuarto parametro con el tipo exacto `CameraRenderState`.
- Error corregido: el `@Redirect` del renderer moderno usaba `Object` para el parametro `Model`, lo que rompia la firma del hook de `submitModel`.
- Como se corrigio: `kct$submitTintedCrystalModel` ahora recibe `Model` como tipo exacto y mantiene el `render state` como `Object`, que es lo que la llamada vanilla espera.
- Error corregido: el crystal podia deformarse visualmente al colocarse o al activar `Crystal Flotation`, porque la animacion moderna estaba moviendo `innerGlass` y `cube` como si no fueran hijas de `outerGlass`.
- Como se corrigio: la flotacion vuelve a aplicar el offset vanilla solo sobre `outerGlass`, dejando que la jerarquia del modelo arrastre las partes internas sin desalinearlas.
- Se actualizo `fabric.mod.json` para declarar `>=1.21.9 <1.21.10`.
- Verificacion completada: `./gradlew.bat clean build` compila limpio.
