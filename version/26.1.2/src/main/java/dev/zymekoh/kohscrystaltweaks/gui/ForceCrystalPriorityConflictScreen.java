package dev.zymekoh.kohscrystaltweaks.gui;

import dev.zymekoh.kohscrystaltweaks.compat.ForceCrystalPriorityCompatibility;
import dev.zymekoh.kohscrystaltweaks.compat.ForceCrystalPriorityConflictScanner.Conflict;
import dev.zymekoh.kohscrystaltweaks.compat.ForceCrystalPriorityConflictScanner.ConflictPoint;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Optional, feature-scoped warning with an explicit continue or cancel decision. */
public final class ForceCrystalPriorityConflictScreen extends Screen {
    private static final Component TITLE = Component.literal("Crystal Priority Conflict");
    private static final AtomicBoolean STARTUP_REGISTERED = new AtomicBoolean();

    private final Screen parent;
    private final List<Conflict> conflicts;
    private final Runnable continueAction;
    private final Runnable cancelAction;
    private boolean resolved;

    public ForceCrystalPriorityConflictScreen(
            Screen parent,
            List<Conflict> conflicts,
            Runnable continueAction,
            Runnable cancelAction
    ) {
        super(TITLE);
        this.parent = parent;
        this.conflicts = List.copyOf(conflicts);
        this.continueAction = continueAction;
        this.cancelAction = cancelAction;
    }

    public static void registerStartupWarning() {
        ForceCrystalPriorityCompatibility.initialize();
        if (!ForceCrystalPriorityCompatibility.isStartupDecisionPending()
                || !STARTUP_REGISTERED.compareAndSet(false, true)) {
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!ForceCrystalPriorityCompatibility.isStartupDecisionPending()
                    || client.getOverlay() != null
                    || client.screen instanceof ForceCrystalPriorityConflictScreen) {
                return;
            }

            client.setScreen(new ForceCrystalPriorityConflictScreen(
                    client.screen,
                    ForceCrystalPriorityCompatibility.getStartupConflicts(),
                    ForceCrystalPriorityCompatibility::continueAnyway,
                    ForceCrystalPriorityCompatibility::cancelAndDisable));
        });
    }

    @Override
    protected void init() {
        int margin = Math.max(8, Math.min(20, width / 18));
        int titleY = titleY();
        int bodyTop = titleY + font.lineHeight + 6;
        int buttonHeight = 20;
        int buttonGap = 6;
        int availableWidth = Math.max(1, width - margin * 2);
        boolean stackButtons = availableWidth < 246;
        int footerHeight = stackButtons ? buttonHeight * 2 + buttonGap + 12 : buttonHeight + 12;
        int bodyHeight = Math.max(20, height - bodyTop - footerHeight);

        addRenderableWidget(new FittingMultiLineTextWidget(
                margin,
                bodyTop,
                availableWidth,
                bodyHeight,
                Component.literal(buildMessage(conflicts)),
                font));

        if (stackButtons) {
            int buttonWidth = Math.max(1, Math.min(200, availableWidth));
            int buttonX = (width - buttonWidth) / 2;
            int firstY = Math.max(bodyTop + 20, height - buttonHeight * 2 - buttonGap - 6);
            addRenderableWidget(Button.builder(
                            Component.literal("Continue anyway"),
                            button -> resolve(true))
                    .bounds(buttonX, firstY, buttonWidth, buttonHeight)
                    .build());
            addRenderableWidget(Button.builder(
                            Component.literal("Cancel"),
                            button -> resolve(false))
                    .bounds(buttonX, firstY + buttonHeight + buttonGap, buttonWidth, buttonHeight)
                    .build());
            return;
        }

        int buttonWidth = Math.max(1, Math.min(160, (availableWidth - buttonGap) / 2));
        int totalWidth = buttonWidth * 2 + buttonGap;
        int buttonX = (width - totalWidth) / 2;
        int buttonY = Math.max(bodyTop + 20, height - buttonHeight - 6);
        addRenderableWidget(Button.builder(
                        Component.literal("Continue anyway"),
                        button -> resolve(true))
                .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Cancel"),
                        button -> resolve(false))
                .bounds(buttonX + buttonWidth + buttonGap, buttonY, buttonWidth, buttonHeight)
                .build());
    }

    @Override
    public void onClose() {
        resolve(false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0100C12);
        int frameMargin = frameMargin();
        graphics.fill(frameMargin, frameMargin, width - frameMargin, height - frameMargin, 0xE51A111D);
        graphics.outline(frameMargin, frameMargin, Math.max(1, width - frameMargin * 2),
                Math.max(1, height - frameMargin * 2), 0xFFFFB84D);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, TITLE, width / 2, titleY(), 0xFFFFC96B);
    }

    private void resolve(boolean continueAnyway) {
        if (resolved) {
            return;
        }
        resolved = true;
        if (continueAnyway) {
            continueAction.run();
        } else {
            cancelAction.run();
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private int frameMargin() {
        return Math.max(4, Math.min(12, width / 32));
    }

    private int titleY() {
        return frameMargin() + 4;
    }

    private static String buildMessage(List<Conflict> conflicts) {
        StringBuilder message = new StringBuilder();
        message.append("Force Crystal PvP Priority found another mod on its explicit hotbar-selection input path.\n\n");
        appendConflicts(message, conflicts, false);
        message.append("Both mixins target the keybinding callback reached after the player presses a bound hotbar "
                + "slot on the keyboard or mouse. KoHs never chooses an item automatically; it only applies that explicitly selected "
                + "slot early when the slot already contains an End Crystal or obsidian. An undefined callback order, "
                + "cancellation, or overwrite can delay or replace the selection, or make mixin application fail and "
                + "crash Minecraft. "
                + "Only Force Crystal PvP Priority is paused; the other KoHs features remain available.\n\n"
                + "Continue anyway accepts that risk and enables this feature. Cancel keeps it disabled.\n\n"
                + "ESPA\u00D1OL\n\n"
                + "Force Crystal PvP Priority encontr\u00F3 otro mod en la misma ruta de entrada usada para seleccionar "
                + "la hotbar.\n\n");
        appendConflicts(message, conflicts, true);
        message.append("Ambos mixins apuntan al callback de keybind alcanzado despu\u00E9s de que el jugador pulsa el "
                + "slot enlazado en el teclado o mouse. KoHs nunca elige un objeto autom\u00E1ticamente; solo aplica antes "
                + "el slot seleccionado por el jugador cuando ya contiene un End Crystal u obsidiana. Un orden de "
                + "callbacks indefinido, una cancelaci\u00F3n o un overwrite puede retrasar o reemplazar la selecci\u00F3n, "
                + "o hacer fallar la aplicaci\u00F3n del mixin y cerrar Minecraft. Solo Force Crystal PvP Priority queda "
                + "en pausa; las dem\u00E1s funciones de KoHs siguen disponibles.\n\n"
                + "Continue anyway acepta el riesgo y activa esta funci\u00F3n. Cancel la mantiene desactivada.");
        return message.toString();
    }

    private static void appendConflicts(StringBuilder message, List<Conflict> conflicts, boolean spanish) {
        for (Conflict conflict : conflicts) {
            message.append("\u2022 ").append(conflict.modName())
                    .append(" (").append(conflict.modId()).append(' ')
                    .append(conflict.version()).append(")\n");
            for (ConflictPoint point : conflict.points()) {
                message.append(spanish ? "  Mixin externo: " : "  Foreign mixin: ")
                        .append(point.mixinClass()).append('\n')
                        .append(spanish ? "  Clase / m\u00E9todo: " : "  Class / method: ")
                        .append(point.targetClass()).append('#').append(point.targetMethod()).append('\n');
            }
            message.append('\n');
        }
    }
}
