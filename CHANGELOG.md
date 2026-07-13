# Changelog

## 1.1.0-beta.1 — 2026-07-12

### Común a 1.21.10, 1.21.11 y 26.1.2

- Integrado KoHs Crystal Placement Fix dentro de KoHs Crystal Tweaks.
- Añadido `placementFixEnabled=true` a la configuración persistente.
- Añadido toggle `Placement Fix` a `Tweaks` con confirmación `Aceptar` / `Restablecer` al desactivarlo.
- Retarget limitado a la interacción vanilla actual, sin paquetes adicionales ni automatización.
- Predicción local movida al resultado aceptado de la interacción.
- Timeout adaptativo inicial elevado al valor configurado.
- Bases de predicción alineadas con vanilla: obsidiana o bedrock.
- Tinte por pieza aplicado durante el consumo real de la cola de render.

### 1.21.10 y 1.21.11

- Reemplazado el render fragmentado que mutaba `visible` en piezas encoladas.
- Config screen adaptado a tamaños lógicos compactos y escalas GUI altas.

### 26.1.2

- Portados tintes separados para frame/core.
- Portados `Spin Speed`, `Crystal Flotation` y `Static Crystal` al modelo y al beam.
- Integrado Placement Fix con nombres oficiales de Mojang y Java 25.

