# Crystal Tweaks KoHs 2.2.7

## English

### Highlights

- Reduced crystal desynchronization after a locally predicted break. Prediction now runs only for
  valid local candidates: the crystal must still exist, be within the player's interaction range,
  and the player must be alive, not spectating, and able to deal damage.
- Fixed rapid Use actions targeting a crystal that had already been removed locally. After a
  predicted break, Crystal Tweaks now repeats Minecraft's own complete crosshair pick so blocks and
  entities are selected with Vanilla's normal ray and ranges.
- Fixed cases where a quick placement was lost while still consuming Vanilla's four-tick Use delay.
- Moved local break prediction onto the Minecraft client thread, preventing unsafe access to client
  entity state from the network path.
- Corrected the Fabric Loader requirement in every JAR. Older builds could request a Loader version
  that did not exist for their Minecraft release and refuse to start.
- Each JAR now declares exactly one Minecraft version, so Modrinth no longer detects a build such as
  1.21 as compatible with the entire 1.21-to-1.21.11 range.

### Other changes

- The mod name is now consistently displayed as **Crystal Tweaks KoHs**.
- Instant crystal-break prediction remains part of the core and has no toggle. Obsolete pre-release
  gameplay settings are removed automatically when the configuration is saved.
- The multi-version build system was rebuilt around one verified dependency matrix. All 16 JARs,
  covering Minecraft **1.21 through 26.2**, were checked against their exact Minecraft classes,
  declared dependencies, Java version, Mixin targets, and SHA-256 checksum.

### Multiplayer notice

This update does not reset attack or Use cooldowns, extend reach, rotate the player, automate clicks,
or duplicate gameplay packets. It does use local crystal-break prediction, which can affect the
target or timing of a later action performed by the player compared with waiting for server
confirmation. Server rules differ, so check the rules of the server you play on.

---

## Español

### Cambios principales

- Se redujo la desincronización de cristales después de una rotura predicha localmente. La
  predicción ahora solo se aplica a candidatos válidos en el cliente: el cristal debe seguir
  existiendo, estar dentro del alcance de interacción y el jugador debe estar vivo, no ser
  espectador y poder causar daño.
- Se corrigieron las acciones rápidas de Usar que seguían apuntando a un cristal ya eliminado
  localmente. Después de una rotura predicha, Crystal Tweaks vuelve a ejecutar la selección completa
  de la mira de Minecraft para detectar bloques y entidades con el rayo y los alcances de Vanilla.
- Se corrigieron casos donde una colocación rápida se perdía, pero aun así consumía los cuatro ticks
  de espera de Usar de Vanilla.
- La predicción local de rotura ahora se ejecuta en el hilo principal del cliente, evitando acceso
  inseguro al estado de las entidades desde la ruta de red.
- Se corrigió el requisito de Fabric Loader de cada JAR. Algunas compilaciones anteriores exigían
  una versión de Loader inexistente para su versión de Minecraft y no podían iniciarse.
- Cada JAR ahora declara una única versión exacta de Minecraft, evitando que Modrinth detecte una
  compilación como la 1.21 para todo el rango desde 1.21 hasta 1.21.11.

### Otros cambios

- El nombre ahora se muestra de forma consistente como **Crystal Tweaks KoHs**.
- La predicción de rotura instantánea continúa siendo parte del núcleo y no tiene interruptor. Las
  opciones obsoletas de versiones preliminares se eliminan al guardar la configuración.
- El sistema multiversión fue reconstruido alrededor de una matriz única de dependencias. Los 16
  JAR, desde Minecraft **1.21 hasta 26.2**, fueron comprobados contra sus clases reales de Minecraft,
  dependencias declaradas, versión de Java, objetivos Mixin y checksum SHA-256.

### Aviso para multijugador

Esta actualización no reinicia los cooldowns de ataque o de Usar, no amplía el alcance, no gira al
jugador, no automatiza clics y no duplica paquetes de juego. Sí utiliza predicción local de rotura,
lo que puede afectar el objetivo o el momento de una acción posterior realizada por el jugador en
comparación con esperar la confirmación del servidor. Las reglas cambian según el servidor;
consulta siempre las reglas del servidor donde juegas.
