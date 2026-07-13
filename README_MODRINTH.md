# KoHs Crystal Tweaks

KoHs Crystal Tweaks es un mod cliente para Fabric enfocado en crystals PvP. Mantiene los cristales locales, suaviza la transicion al crystal real del servidor, permite tint personalizado y deja importar un sonido custom para la explosion.

## Caracteristicas
- Placement Fix integrado para retargetear el clic actual a la obsidiana recién predicha, sin paquetes extra ni automatización.
- Local Crystal para que la colocacion se vea instantanea en cliente.
- Seamless Mode para suavizar el cambio entre crystal local y crystal del servidor.
- Crystal Optimizer legit inspirado en Marlow: el crystal se limpia del lado del cliente al pegarlo para reducir el delay visual, sin automatizar golpes.
- Crystal Tint para recolorear outer/core del End Crystal.
- Custom Sound con importacion `WAV`, `OGG` y `MP3`.
- El sonido custom reemplaza `minecraft:entity.generic.explode` desde la tuberia vanilla de Minecraft, asi que se comporta como un resource pack runtime y evita el delay del sistema manual.
- La pantalla de configuracion del mod usa su propio fondo y no deja que el blur vanilla invada el menu.

## Versiones soportadas en este repositorio
- `1.21 - 1.21.1`
- `1.21.2 - 1.21.4`
- `1.21.5`
- `1.21.6 - 1.21.8`
- `1.21.9`
- `1.21.10`
- `1.21.11`
- `26.1.2`

## Notas
- Mod cliente para Fabric.
- Las builds modernas `1.21.9+` incluyen compatibilidad con el protocolo moderno de `Marlow's Crystal Optimizer` (`version`, `opt_out`, `opt_out_ack`) para que un servidor pueda desactivar el optimizador de forma limpia si asi lo requiere.
- El ajuste `Anchor Charge` fue retirado; el sistema de sonido custom ahora se centra solo en el reemplazo de explosiones vanilla.
- Los bloques intermedios se dividieron para respetar cambios reales de API entre subversiones y evitar crashes por compatibilidad binaria falsa.
- Cada carpeta de `version/` compila de forma independiente.

## Configuracion
- Abre la pantalla del mod.
- Activa `Custom Sound`.
- Importa un archivo compatible.
- Ajusta volumen y velocidad.

## Build
Cada version se compila dentro de su propia carpeta con:

```bash
./gradlew.bat clean build
```
