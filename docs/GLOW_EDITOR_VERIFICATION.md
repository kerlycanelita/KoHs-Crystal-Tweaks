# Glow editor — revisión de 1.21.11 y ports 26.x

## Problemas encontrados

- El glow anterior enviaba un segundo modelo sin la escala y traslación del renderizador vanilla. Por eso parecía un cristal más pequeño dentro del original.
- El botón Glow reutilizaba el editor de capas y sus controles de giro/flotación.
- La advertencia del color era solo un tooltip: no pedía confirmación.
- Faltaban reflejos y un perfil visual independiente para cristales ajenos.

## Implementación

- Ventana Glow con preview centrada, potencia, reflejos y selector de color propio. Sin controles de Exterior/Interior/Núcleo ni velocidades de animación.
- Halo radial aditivo, con degradado de alfa y prueba de profundidad. 100% conserva la intensidad anterior; el slider ahora permite 0–300%. No se vuelve a dibujar el modelo del cristal; la luz del render state aumenta con la potencia.
- Centro cristalino sobreexpuesto y rayos finos teñidos por el color elegido, con alfa saturada para que 300% no desborde el formato de color.
- Al desaparecer un cristal, el brillo residual se conserva como estado visual independiente durante 1,2 segundos y cae suavemente. No mantiene ni resucita la entidad.
- Confirmación antes de sustituir visualmente los colores por capa. Los valores originales se conservan.
- Perfil de cristales ajenos con capas, giro, flotación y su propio editor de glow. La preview muestra el perfil editado aunque todavía no esté activado en el mundo.
- Copias de apariencia por render state, aplicadas al modelo justo antes de dibujarlo. Los perfiles no dependen del orden de dibujo de los cristales.
- Controles desplazables; la preview se reduce o desaparece cuando no cabe. Se conserva la interacción y el sonido de la preview.

## Límites que deben comunicarse

Los reflejos son **luz de color simulada sobre las caras superiores de bloques cercanos**, no iluminación dinámica del motor ni reflejos de trazado de rayos. Se comprueban superficies y oclusión, sin cargar chunks; alcance de superficies limitado y caché de 250 ms, con un máximo de ocho muestreos por tick. Halo limitado a 64 bloques y superficies a 32 bloques de la cámara. Potencia cero desactiva ambos efectos; 100% es la referencia anterior y 300% es el máximo.

La identificación de cristales ajenos es **aproximada**. Se relaciona una aparición con un intento local de colocación en la misma base durante tres segundos. Los que no coinciden, incluidos los de propietario desconocido, usan el perfil ajeno si está activado. El protocolo vanilla no proporciona el propietario; colocaciones simultáneas, rechazos y latencias altas pueden causar atribuciones incorrectas. No se utiliza esta clasificación para ataques, selección de objetivos o paquetes.

No se han modificado las optimizaciones existentes del core, entradas, cámara, cooldowns ni envío de paquetes. El observador de colocación existente ahora guarda información local para la clasificación visual. Esto no certifica compatibilidad con todos los shaders, renderizadores o servidores.

## Verificación sin abrir Minecraft

- Compilaciones seleccionadas: 1.21.11, 26.1, 26.1.2 y 26.2.
- `tools/test-glow.ps1`: aislamiento entre perfiles y snapshots, conservación del color, límites de valores y 42.625 combinaciones de distribución.
- `tools/verify-jars.ps1 -Path versions/glow-editor`: dependencias y versión exacta de Minecraft.
- `tools/verify-mixins.py`: clases y métodos reales de cada target; no sustituye una carga real de Mixin durante el arranque.
- JAR de prueba en `versions/glow-editor/`, separados de las compilaciones anteriores. No se publica ni se instala automáticamente.

## Prueba visual pendiente del usuario

1. Abrir Visuals → Glow; comprobar potencia 0/50/100, ausencia de cristal duplicado y ausencia de controles de capas.
2. Probar confirmar/cancelar el color propio, cambiar su hexadecimal, volver y desactivarlo. Los colores anteriores deben restaurarse.
3. Probar reflejos 0/100 con potencia mayor que cero, en obsidiana, escalones, detrás de paredes y al retirar un bloque. La preview usa un plano de luz, sin soporte de bedrock.
4. Poner giro/flotación en 0/100/300 desde Visuals; el halo debe seguir el centro del cristal. Probar ataque/uso y las teclas remapeadas en la preview.
5. Elegir colores distintos para jugador/ajenos, activar el perfil ajeno y colocar con dos jugadores. Considerar los límites de atribución descritos arriba.
6. Revisar escala GUI Auto/2x/3x/4x, ventanas pequeñas, cambio de tamaño, español/inglés, resource packs y shaders habituales.

No se han abierto instancias de Minecraft; la apariencia final, los FPS y la compatibilidad visual con shaders siguen pendientes de esa prueba.
