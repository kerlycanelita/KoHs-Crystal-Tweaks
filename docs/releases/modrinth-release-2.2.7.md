# Crystal Tweaks KoHs 2.2.7 — guion de publicacion en Modrinth

Proyecto: https://modrinth.com/mod/kohs-crystal-tweaks

Convencion existente: una version de Modrinth por version de Minecraft, nombre `<mod>+mc<minecraft>`, canal Release, loader Fabric, entorno solo cliente.

## Ajustes iguales para las 16 versiones

- **Release channel:** Release
- **Loaders:** Fabric
- **Game versions:** solo la que indica la tabla, una por version
- **Dependencias:** Fabric API (required) · Mod Menu (optional)
- **Nombre del mod en el JAR:** ahora `Crystal Tweaks KoHs` (antes `Crystal Tweaks`),
  para que coincida con el proyecto de Modrinth. Los nombres de archivo siguen siendo
  `crystal-tweaks-*.jar` por coherencia con las 74 versiones ya publicadas.

## Tabla de subida

| # | Nombre de version | Archivo | MC | Java | Fabric Loader minimo |
|---|---|---|---|---|---|
| 1 | `2.2.7+mc26.2` | `crystal-tweaks-26.2-2.2.7.jar` | 26.2 | 25 | `>=0.19.3` |
| 2 | `2.2.7+mc26.1.2` | `crystal-tweaks-26.1.2-2.2.7.jar` | 26.1.2 | 25 | `>=0.18.6` |
| 3 | `2.2.7+mc26.1.1` | `crystal-tweaks-26.1.1-2.2.7.jar` | 26.1.1 | 25 | `>=0.18.6` |
| 4 | `2.2.7+mc26.1` | `crystal-tweaks-26.1-2.2.7.jar` | 26.1 | 25 | `>=0.18.6` |
| 5 | `2.2.7+mc1.21.11` | `crystal-tweaks-1.21.11-2.2.7.jar` | 1.21.11 | 21 | `>=0.18.4` |
| 6 | `2.2.7+mc1.21.10` | `crystal-tweaks-1.21.10-2.2.7.jar` | 1.21.10 | 21 | `>=0.18.4` |
| 7 | `2.2.7+mc1.21.9` | `crystal-tweaks-1.21.9-2.2.7.jar` | 1.21.9 | 21 | `>=0.17.2` |
| 8 | `2.2.7+mc1.21.8` | `crystal-tweaks-1.21.8-2.2.7.jar` | 1.21.8 | 21 | `>=0.17.2` |
| 9 | `2.2.7+mc1.21.7` | `crystal-tweaks-1.21.7-2.2.7.jar` | 1.21.7 | 21 | `>=0.16.9` |
| 10 | `2.2.7+mc1.21.6` | `crystal-tweaks-1.21.6-2.2.7.jar` | 1.21.6 | 21 | `>=0.16.9` |
| 11 | `2.2.7+mc1.21.5` | `crystal-tweaks-1.21.5-2.2.7.jar` | 1.21.5 | 21 | `>=0.16.9` |
| 12 | `2.2.7+mc1.21.4` | `crystal-tweaks-1.21.4-2.2.7.jar` | 1.21.4 | 21 | `>=0.16.9` |
| 13 | `2.2.7+mc1.21.3` | `crystal-tweaks-1.21.3-2.2.7.jar` | 1.21.3 | 21 | `>=0.16.9` |
| 14 | `2.2.7+mc1.21.2` | `crystal-tweaks-1.21.2-2.2.7.jar` | 1.21.2 | 21 | `>=0.16.9` |
| 15 | `2.2.7+mc1.21.1` | `crystal-tweaks-1.21.1-2.2.7.jar` | 1.21.1 | 21 | `>=0.16.9` |
| 16 | `2.2.7+mc1.21` | `crystal-tweaks-1.21-2.2.7.jar` | 1.21 | 21 | `>=0.16.9` |

## Changelog (English)

### Fixed

**Crystals that stayed invisible and blocked later placements.**
When you hit a crystal, the mod hides it immediately instead of waiting for the server. Until now it
did that even when the server refused the hit, so the server kept a crystal your client could no
longer see, and every later placement on that obsidian was silently rejected with nothing shown on
screen. The prediction now only runs for a hit the server is expected to accept.

**Clicks after a break landing on the wrong block, or doing nothing at all.**
Breaking a crystal invalidates the crosshair target Minecraft computed at the start of the tick. The
refresh that followed used a block-only ray cast, so with a second crystal in your line of sight it
targeted the block behind it. It now re-runs Minecraft's own pick, which handles blocks and entities
with vanilla's own ranges. This is most noticeable if you bind Attack and Use to separate keys or
mouse buttons, since both can fire inside the same tick.

**The mod refusing to load on older Fabric Loader versions.**
Every build except 26.2 shipped a `fabricloader >=0.19.3` requirement that its Minecraft version
never had a release for. Each JAR now declares the loader it was actually built against.

### Changed

- Instant crystal break is core behaviour with no setting. It runs only when the crystal is inside
  your interaction range, is not already removed, and you are alive, not spectating and able to deal
  damage. Anything outside that waits for the server exactly like vanilla.

### Multiplayer

Crystal Tweaks never fabricates, delays, reorders or duplicates a gameplay packet, and never changes
reach, rotation, attack cooldown or click rate. Your aim and your click rate are entirely vanilla.
The full client/server boundary, including the two places the mod does change what a later packet
contains, is documented in `docs/LEGITIMACY_AUDIT.md` in the repository.

Server rules differ. Check the rules of your server and ask staff when in doubt.


## Changelog (Espanol)

### Corregido

**Cristales que quedaban invisibles y bloqueaban colocaciones posteriores.**
Al golpear un cristal, el mod lo oculta al instante en vez de esperar al servidor. Hasta ahora lo
hacia incluso cuando el servidor rechazaba el golpe, asi que el servidor conservaba un cristal que
tu cliente ya no veia, y toda colocacion posterior sobre esa obsidiana se rechazaba en silencio sin
mostrar nada. La prediccion ahora solo se ejecuta para golpes que el servidor va a aceptar.

**Clics tras romper que caian en el bloque equivocado, o que no hacian nada.**
Romper un cristal invalida el objetivo de la mira que Minecraft calculo al principio del tick. El
refresco que venia despues usaba un raycast solo de bloques, asi que con otro cristal en la linea de
vision apuntaba al bloque de detras. Ahora se reejecuta el pick propio de Minecraft, que maneja
bloques y entidades con los rangos de vanilla. Se nota sobre todo con Atacar y Usar en teclas o
botones separados, porque ambos pueden dispararse dentro del mismo tick.

**El mod se negaba a cargar en versiones antiguas de Fabric Loader.**
Todas las builds salvo la de 26.2 exigian `fabricloader >=0.19.3`, una version que nunca existio
para su version de Minecraft. Ahora cada JAR declara el loader contra el que se construyo de verdad.

### Cambiado

- La rotura instantanea es comportamiento del nucleo y no tiene ajuste. Solo se ejecuta si el cristal
  esta dentro de tu rango de interaccion, no esta ya eliminado, y tu estas vivo, no en espectador y
  puedes hacer dano. Todo lo demas espera al servidor igual que vanilla.

### Multijugador

Crystal Tweaks nunca fabrica, retrasa, reordena ni duplica un paquete de juego, y nunca cambia el
alcance, la rotacion, el cooldown de ataque ni el ritmo de clic. Tu punteria y tu cadencia son
enteramente vanilla. La frontera cliente/servidor completa, incluidos los dos puntos donde el mod si
cambia lo que contiene un paquete posterior, esta documentada en `docs/LEGITIMACY_AUDIT.md`.

Las reglas cambian segun el servidor. Consulta las de tu servidor y pregunta al staff si dudas.


## SHA-256

```
d1e984e37105056b0e9d726d41af646708dd5d88b589c6ce93933aeda81abef9  crystal-tweaks-26.2-2.2.7.jar
d7eb22f61ddca3bd15d3d18cc90445012495e3a33f4afeeaa3de891fd60eecc5  crystal-tweaks-26.1.2-2.2.7.jar
0f66cb0e8f8b9ad323fdf3e0490a839b2c2f9225bd737abe5d0825df173509ed  crystal-tweaks-26.1.1-2.2.7.jar
1d76387af6170b874164ce519ee9a1245ddcdf9dec7b10853a923f63c9620603  crystal-tweaks-26.1-2.2.7.jar
e2b4b5db77b70bcac3a54fc369d74599646c64c1055afa6fec4e2b42bf67565e  crystal-tweaks-1.21.11-2.2.7.jar
5ad59feb4792fb83515e71939bef2777a60280a7b87d4ca725db40c19efd4513  crystal-tweaks-1.21.10-2.2.7.jar
419078f606cfc19b9947143759277459be8a59e75b7eb7d0ffec96127aecfede  crystal-tweaks-1.21.9-2.2.7.jar
e7f0e7d54bf24721aa488be2a4f9bcb0eb9253eff565605ed7db642bf662997c  crystal-tweaks-1.21.8-2.2.7.jar
432282433cc96ec39b40cf837b2220fa1f54c95bbe6a9c759e7f35c2832e2068  crystal-tweaks-1.21.7-2.2.7.jar
9f89c0feb68457a8568a0240f914983264ad7c64e975dc00c55c3c971ebc5aa7  crystal-tweaks-1.21.6-2.2.7.jar
ab4c59043490589198685dbb696a7bc7bb85dd480c13caafb9d9e2fd3aae4568  crystal-tweaks-1.21.5-2.2.7.jar
87c62c26094f2090168737e8fdd1247534fd9bf505ffed67677413aef0924608  crystal-tweaks-1.21.4-2.2.7.jar
7956c2efbe43d05031605950f1333aabf51ba2dad5c7723c539cd0a1e38e5e3b  crystal-tweaks-1.21.3-2.2.7.jar
35ffc96a337c47347ea80bc9c13c6d546980cc6c35ebef76d05a2e3135305f65  crystal-tweaks-1.21.2-2.2.7.jar
8b794f1a5ca5ea45edd7b16d725a8b88825751e827980e7dd5b17da729d0d245  crystal-tweaks-1.21.1-2.2.7.jar
1cf7ca440415253ace6334ebf05eed46e8b7b6b0cd6757f03aece6b095d7a1bf  crystal-tweaks-1.21-2.2.7.jar
```
