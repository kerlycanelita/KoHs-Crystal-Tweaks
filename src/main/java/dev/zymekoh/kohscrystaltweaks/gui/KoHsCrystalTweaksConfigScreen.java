package dev.zymekoh.kohscrystaltweaks.gui;

import dev.zymekoh.kohscrystaltweaks.config.KoHsCrystalTweaksConfig;
import dev.zymekoh.kohscrystaltweaks.core.CrystalPredictor;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public final class KoHsCrystalTweaksConfigScreen extends Screen {
    private static final Text TITLE = Text.literal("KoHs Crystal Tweaks");
    private static final Text SUBTITLE = Text.literal("Legit Crystal Optimizer");
    private static final Text DESCRIPTION = Text.literal("Smoother local crystal visuals. Server logic unchanged.");
    private static final Text CREDIT = Text.literal(
            "Extracted from KoHs mod suite. For more mods contact zymekoh.");

    private final Screen parent;
    private ButtonWidget toggleButton;

    public KoHsCrystalTweaksConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = 420;
        int panelHeight = 220;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        int centerX = this.width / 2;

        this.toggleButton = ButtonWidget.builder(toggleLabel(), button -> {
                    boolean next = !KoHsCrystalTweaksConfig.get().clientSideCrystalsEnabled;
                    KoHsCrystalTweaksConfig.setClientSideCrystalsEnabled(next);
                    CrystalPredictor.setEnabled(next);
                    button.setMessage(toggleLabel());
                })
                .dimensions(centerX - 95, panelY + 132, 190, 20)
                .build();
        this.addDrawableChild(this.toggleButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> this.close())
                .dimensions(centerX - 55, panelY + panelHeight - 30, 110, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelWidth = 420;
        int panelHeight = 220;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int centerX = this.width / 2;

        // Transparent background: only draw a semi-transparent panel.
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x8A1A0A2E);
        context.drawStrokedRectangle(panelX, panelY, panelWidth, panelHeight, 0xE0B86BFF);

        context.drawCenteredTextWithShadow(this.textRenderer, TITLE, centerX, panelY + 12, 0xFFEAD1FF);
        context.drawCenteredTextWithShadow(this.textRenderer, SUBTITLE, centerX, panelY + 30, 0xFFDDA6FF);
        drawWrappedCentered(context, DESCRIPTION, centerX, panelY + 52, panelWidth - 34, 0xFFE6D9F2);

        drawWrappedCentered(context, CREDIT, centerX, panelY + 92, panelWidth - 34, 0xFFE8D8F8);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private static Text toggleLabel() {
        return Text.literal("Local Crystal: "
                + (KoHsCrystalTweaksConfig.get().clientSideCrystalsEnabled ? "ON" : "OFF"));
    }

    private void drawWrappedCentered(DrawContext context, Text text, int centerX, int y, int maxWidth, int color) {
        List<OrderedText> lines = this.textRenderer.wrapLines(text, maxWidth);
        int lineY = y;
        for (OrderedText line : lines) {
            int lineX = centerX - (this.textRenderer.getWidth(line) / 2);
            context.drawText(this.textRenderer, line, lineX, lineY, color, false);
            lineY += 10;
        }
    }
}
