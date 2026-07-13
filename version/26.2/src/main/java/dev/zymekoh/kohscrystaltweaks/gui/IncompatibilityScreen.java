package dev.zymekoh.kohscrystaltweaks.gui;

import dev.zymekoh.kohscrystaltweaks.compat.IncompatibilityManager;
import dev.zymekoh.kohscrystaltweaks.compat.IncompatibilityManager.Conflict;
import dev.zymekoh.kohscrystaltweaks.compat.IncompatibilityManager.ConflictPoint;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Mandatory, non-dismissible explanation shown when an unsafe mod combination is detected. */
public final class IncompatibilityScreen extends Screen {
    private static final Component TITLE = Component.literal("KoHs - Incompatible Mods");
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    public IncompatibilityScreen() {
        super(TITLE);
    }

    public static void registerBlocker() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (IncompatibilityManager.isBlocked()
                    && !(client.gui.screen() instanceof IncompatibilityScreen)) {
                client.gui.setScreen(new IncompatibilityScreen());
            }
        });
    }

    @Override
    protected void init() {
        int margin = Math.max(8, Math.min(20, width / 18));
        int bodyTop = titleY() + font.lineHeight + 5;
        int buttonHeight = 20;
        int footerSpace = buttonHeight + 18;
        int bodyWidth = Math.max(1, width - margin * 2);
        int bodyHeight = Math.max(20, height - bodyTop - footerSpace);

        addRenderableWidget(new FittingMultiLineTextWidget(
                margin,
                bodyTop,
                bodyWidth,
                bodyHeight,
                Component.literal(buildMessage(IncompatibilityManager.getConflicts())),
                font));

        int buttonWidth = Math.max(1, Math.min(220, width - margin * 2));
        int buttonX = (width - buttonWidth) / 2;
        int buttonY = Math.max(bodyTop + 20, height - buttonHeight - 8);
        addRenderableWidget(Button.builder(
                        Component.literal("Close Minecraft / Cerrar Minecraft"),
                        button -> stopClient())
                .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
                .build());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        stopClient();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0100C12);
        int margin = frameMargin();
        graphics.fill(margin, margin, width - margin, height - margin, 0xE51A111D);
        graphics.outline(margin, margin, Math.max(1, width - margin * 2),
                Math.max(1, height - margin * 2), 0xFFE04B4B);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, TITLE, width / 2, titleY(), 0xFFFF6B6B);
    }

    private int frameMargin() {
        return Math.max(4, Math.min(12, width / 32));
    }

    private int titleY() {
        return frameMargin() + 4;
    }

    private void stopClient() {
        if (minecraft != null) {
            minecraft.stop();
        }
    }

    private static String buildMessage(List<Conflict> conflicts) {
        StringBuilder english = new StringBuilder();
        english.append("KoHs Crystal Tweaks requires you to remove the following incompatible mod(s):\n\n");
        appendConflicts(english, conflicts, false);
        english.append("\nKoHs has disabled all gameplay mixins and runtime services before they could alter Minecraft. "
                + "Continuing is blocked because the combined callback order is not a compatibility contract and can "
                + "corrupt crystal placement, attack prediction, or entity cleanup state. Remove the listed mod(s), "
                + "then restart Minecraft.\n\n");

        StringBuilder spanish = new StringBuilder();
        spanish.append("ESPAÑOL\n\nKoHs Crystal Tweaks necesita que retires los siguientes mods incompatibles:\n\n");
        appendConflicts(spanish, conflicts, true);
        spanish.append("\nKoHs desactivó todos sus mixins de jugabilidad y servicios de ejecución antes de que pudieran "
                + "modificar Minecraft. No se permite continuar porque el orden combinado de callbacks no es un "
                + "contrato de compatibilidad y puede corromper el estado de colocación, predicción de ataques o "
                + "limpieza de entidades de cristal. Retira los mods indicados y reinicia Minecraft.");
        return english.append(spanish).toString();
    }

    private static void appendConflicts(StringBuilder output, List<Conflict> conflicts, boolean spanish) {
        for (Conflict conflict : conflicts) {
            output.append("• ").append(conflict.modName())
                    .append(" (").append(conflict.modId()).append(' ')
                    .append(conflict.version()).append(")\n");

            switch (conflict.type()) {
                case KNOWN_CRYSTAL_OPTIMIZER -> output.append(spanish
                        ? "  Razón: KoHs y Marlow registran los mismos identificadores de compatibilidad, incluido "
                                + "marlowcrystal:opt_out; Fabric rechaza el registro duplicado antes del menú principal. "
                                + "Ambos también eliminan cristales localmente y redirigen el objetivo tras los paquetes "
                                + "de ataque, lo que puede duplicar la limpieza y corromper estados pendientes.\n"
                        : "  Reason: KoHs and Marlow register the same compatibility payload identifiers, including "
                                + "marlowcrystal:opt_out; Fabric rejects that duplicate registration before the title "
                                + "screen. Both also remove crystals client-side and retarget after attack packets, "
                                + "which can duplicate cleanup and corrupt pending placement or attack state.\n");
                case MIXIN_OVERLAP -> output.append(spanish
                        ? "  Razón: el mod inyecta en las mismas clases y métodos críticos de cristales que KoHs. "
                                + "El resultado depende de un orden de aplicación no garantizado.\n"
                        : "  Reason: the mod injects into the same timing-critical crystal classes and methods as KoHs. "
                                + "The result depends on an undefined application order.\n");
                case DIRECT_KOHS_MUTATION -> output.append(spanish
                        ? "  Razón: el mod apunta directamente a clases internas de KoHs, por lo que sus invariantes "
                                + "de seguridad ya no se pueden garantizar.\n"
                        : "  Reason: the mod directly targets internal KoHs classes, so KoHs can no longer guarantee "
                                + "its safety invariants.\n");
            }

            if (!conflict.points().isEmpty()) {
                output.append(spanish ? "  Puntos detectados: " : "  Detected points: ");
                for (int index = 0; index < conflict.points().size(); index++) {
                    ConflictPoint point = conflict.points().get(index);
                    if (index > 0) {
                        output.append(", ");
                    }
                    output.append(point.targetClass()).append('#').append(point.targetMethod());
                }
                output.append('\n');
            }
            output.append('\n');
        }
    }
}
