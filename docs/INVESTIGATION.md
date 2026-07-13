# Investigación y decisiones técnicas

## Señales externas

Los reportes públicos sobre hit-crystalling describen el mismo patrón: tras colocar obsidiana rápidamente, el siguiente uso del cristal puede mantener el objetivo anterior y no colocar. También existen optimizadores client-side que reducen la espera visual creando una entidad local hasta que llega la entidad real del servidor.

Referencias consultadas:

- [Reporte comunitario: el cristal no se coloca al spamear tras la obsidiana](https://www.reddit.com/r/MinecraftPVP/comments/1kwove8/weird_bug_or_glitch_when_practicing_hit_crystal/)
- [Discusión comunitaria sobre consistencia y delay en hit-crystalling](https://www.reddit.com/r/CompetitiveMinecraft/comments/1jbmyhx/what_am_i_doing_wrong_in_hitcrystalling/)
- [Fabric 1.21.9/1.21.10: migración de entidades al renderer por cola](https://fabricmc.net/2025/09/23/1219.html)
- [Página pública de KoHs Crystal Tweaks](https://modrinth.com/mod/kohs-crystal-tweaks)

## Hallazgos en el código

1. En 1.21.10/1.21.11 la predicción se disparaba desde `UseBlockCallback`, antes de conocer el resultado final de la interacción.
2. El timeout adaptativo arrancaba en 4 ticks aunque la configuración declaraba 12. Con latencia superior a unos 200 ms, la predicción podía expirar antes de emparejarse y nunca aprender esa latencia.
3. La validación 1.21.x aceptaba crying obsidian, aunque `EndCrystalItem` vanilla sólo acepta obsidiana o bedrock.
4. El tint 1.21.x encolaba varias piezas y cambiaba temporalmente `ModelPart.visible`. Como el dibujo ocurre después, restaurar la visibilidad antes de consumir la cola podía duplicar subárboles o mezclar colores.
5. En 26.1.2 la UI persistía tint/spin/flotation/static, pero faltaban los hooks que consumían esos valores en el modelo.

## Solución

- Capturar el ítem usado en `interactBlock` / `useItemOn` y actuar sólo si el resultado es aceptado.
- Conservar la posición de la obsidiana predicha durante un máximo de 4 ticks.
- Retargetear únicamente si el clic de cristal original no es ya válido y está en la misma colocación rápida.
- Reutilizar la ruta de paquete vanilla; no llamar manualmente al networking.
- Registrar las piezas del modelo por identidad y cambiar el color en `ModelPart.render`, cuando la cola realmente se consume.

## Límites de la verificación

Las tres variantes se compilan y sus JAR se inspeccionan estáticamente. Por instrucción del propietario no se inició Minecraft; la validación in-game de timing, compatibilidad con servidores y combinación de resource packs queda para la prueba manual de la beta.

