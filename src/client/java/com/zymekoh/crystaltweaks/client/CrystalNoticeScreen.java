package com.zymekoh.crystaltweaks.client;

import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * An important notice shown on the way into the settings: Crystal Tweaks only optimizes what the
 * player sees, and Marlow's Crystal Optimizer is what speeds up what reaches the server.
 *
 * <p>It appears at most once per session and never once the player asks it not to. It never
 * appears with Marlow's Crystal Optimizer or any other crystal optimizer installed: Crystal Tweaks
 * has already stood all of its optimizations down for that mod, so there is nothing to recommend.</p>
 */
final class CrystalNoticeScreen extends Screen {
    private static final String MARLOW_ID = "marlowcrystal";
    private static final long ENTER_NANOS = 420_000_000L;
    private static boolean shownThisSession;

    private final Screen next;
    private final boolean spanish;
    private long openedAt;
    private List<FormattedCharSequence> body = List.of();
    private int cardX;
    private int cardY;
    private int cardWidth;
    private int cardHeight;
    private int bodyTop;
    private int bodyHeight;
    private int bodyContentHeight;
    private int bodyScroll;
    private PurpleCloseButton understood;
    private PurpleCloseButton dismiss;

    CrystalNoticeScreen(Screen next) {
        super(Component.literal("Crystal Tweaks"));
        this.next = next;
        String language = Minecraft.getInstance().getLanguageManager().getSelected()
                .toLowerCase(Locale.ROOT).replace('-', '_');
        this.spanish = language.equals("es") || language.startsWith("es_");
    }

    static boolean shouldShow() {
        return !shownThisSession
                && !CrystalVisualConfig.optimizerNoticeDismissed()
                && !FabricLoader.getInstance().isModLoaded(MARLOW_ID)
                && !CrystalOptimizerGuard.conflictDetected();
    }

    @Override
    protected void init() {
        shownThisSession = true;
        if (this.openedAt == 0L) {
            this.openedAt = System.nanoTime();
        }
        int margin = Mth.clamp(this.width / 20, 8, 24);
        this.cardWidth = Math.max(120, Math.min(340, this.width - margin * 2));
        int textWidth = Math.max(40, this.cardWidth - 28);
        this.body = new ArrayList<>();
        List<Component> paragraphs = paragraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (i > 0) {
                this.body.add(FormattedCharSequence.EMPTY);
            }
            this.body.addAll(this.font.split(paragraphs.get(i), textWidth));
        }
        int lineHeight = this.font.lineHeight + 1;
        this.bodyContentHeight = this.body.size() * lineHeight;
        int header = 30;
        int footer = 30;
        this.cardHeight = Math.max(80, Math.min(this.height - margin * 2, header + this.bodyContentHeight + 8 + footer));
        this.cardX = (this.width - this.cardWidth) / 2;
        this.cardY = (this.height - this.cardHeight) / 2;
        this.bodyTop = this.cardY + header;
        this.bodyHeight = Math.max(10, this.cardHeight - header - footer - 4);
        this.bodyScroll = Mth.clamp(this.bodyScroll, 0, Math.max(0, this.bodyContentHeight - this.bodyHeight));

        int buttonHeight = 18;
        int gap = 6;
        int buttonWidth = Math.max(40, Math.min(150, (this.cardWidth - 28 - gap) / 2));
        int buttonsX = this.cardX + (this.cardWidth - buttonWidth * 2 - gap) / 2;
        int buttonsY = this.cardY + this.cardHeight - buttonHeight - 7;
        this.dismiss = addRenderableWidget(new PurpleCloseButton(buttonsX, buttonsY, buttonWidth, buttonHeight,
                Component.literal(this.spanish ? "No volver a mostrar" : "Don't show again"),
                ignored -> dismissForever()));
        this.understood = addRenderableWidget(new PurpleCloseButton(buttonsX + buttonWidth + gap, buttonsY,
                buttonWidth, buttonHeight, Component.literal(this.spanish ? "Entendido" : "Got it"),
                ignored -> proceed()));
        this.understood.setSelected(true);
    }

    private List<Component> paragraphs() {
        Component marlow = Component.literal("Marlow's Crystal Optimizer").withStyle(ChatFormatting.GOLD);
        if (this.spanish) {
            return List.of(
                    Component.literal("Crystal Tweaks solo optimiza del lado del cliente: el cristal que rompes "
                            + "desaparece al instante en tu pantalla, pero lo que llega al servidor no se acelera."),
                    Component.literal("Para que la mejora llegue también al servidor (tu siguiente cristal se coloca "
                            + "antes), se recomienda ").append(marlow)
                            .append(Component.literal(". Revisa antes las normas de tu servidor.")),
                    Component.literal("Si lo instalas, Crystal Tweaks lo detecta y apaga todas sus optimizaciones para "
                            + "no pelear con él. Colores, brillo y sonidos siguen funcionando."));
        }
        return List.of(
                Component.literal("Crystal Tweaks only optimizes on the client: the crystal you break disappears "
                        + "instantly on your screen, but what reaches the server is not sped up."),
                Component.literal("For an improvement that reaches the server too (your next crystal is placed "
                        + "sooner), ").append(marlow)
                        .append(Component.literal(" is recommended. Check your server's rules first.")),
                Component.literal("If you install it, Crystal Tweaks detects it and turns all of its own "
                        + "optimizations off so the two never fight. Colours, glow and sounds keep working."));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x8A05020A);
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        double seconds = now / 1_000_000_000.0D;
        float enter = CrystalTheme.easeOutCubic(Mth.clamp((now - this.openedAt) / (float) ENTER_NANOS, 0.0F, 1.0F));
        CrystalUi.floatingParticles(graphics, this.width, this.height, seconds);

        int width = Math.max(1, Math.round(this.cardWidth * (0.92F + 0.08F * enter)));
        int height = Math.max(1, Math.round(this.cardHeight * (0.92F + 0.08F * enter)));
        int x = this.cardX + (this.cardWidth - width) / 2;
        int y = this.cardY + (this.cardHeight - height) / 2;
        CrystalUi.panel(graphics, x, y, width, height,
                CrystalTheme.fade(0xEE1B0928, enter), CrystalTheme.fade(0xEE0A0310, enter));
        CrystalUi.roundedOutline(graphics, x, y, width, height, CrystalTheme.fade(0xE0FFC48A, enter));
        if (enter > 0.98F) {
            CrystalUi.comets(graphics, this.cardX, this.cardY, this.cardWidth, this.cardHeight, seconds, 0xFFFFD9A8);
        }

        // An amber warning mark beside the title, pulsing gently.
        int badgeX = this.cardX + 18;
        int badgeY = this.cardY + 15;
        float pulse = 0.5F + 0.5F * (float) Math.sin(seconds * 3.0D);
        int badge = CrystalTheme.fade(CrystalTheme.lerp(0xFFFFB35C, 0xFFFFE0A8, pulse), enter);
        for (int row = -6; row <= 6; row++) {
            int span = 6 - Math.abs(row);
            graphics.fill(badgeX - span, badgeY + row, badgeX + span + 1, badgeY + row + 1, badge);
        }
        CrystalUi.centered(graphics, this.font, "!", badgeX + 1, badgeY - 3, CrystalTheme.fade(0xFF2A1238, enter));
        CrystalUi.label(graphics, this.font, this.spanish ? "Aviso importante" : "Important notice",
                badgeX + 12, badgeY - 4, CrystalTheme.fade(0xFFFFE3C2, enter));
        graphics.fill(this.cardX + 12, this.cardY + 25, this.cardX + this.cardWidth - 12, this.cardY + 26,
                CrystalTheme.fade(0x66FFC48A, enter));

        graphics.enableScissor(this.cardX + 10, this.bodyTop, this.cardX + this.cardWidth - 10,
                this.bodyTop + this.bodyHeight);
        int lineY = this.bodyTop - this.bodyScroll;
        int textColor = CrystalTheme.fade(CrystalTheme.TEXT, enter);
        if (((textColor >>> 24) & 255) >= 8) {
            for (FormattedCharSequence line : this.body) {
                graphics.text(this.font, line, this.cardX + 14, lineY, textColor, false);
                lineY += this.font.lineHeight + 1;
            }
        }
        graphics.disableScissor();
        if (this.bodyContentHeight > this.bodyHeight) {
            int trackX = this.cardX + this.cardWidth - 8;
            int thumbHeight = Math.max(8, this.bodyHeight * this.bodyHeight / this.bodyContentHeight);
            int travel = Math.max(1, this.bodyHeight - thumbHeight);
            int maxScroll = Math.max(1, this.bodyContentHeight - this.bodyHeight);
            int thumbY = this.bodyTop + Math.round(travel * this.bodyScroll / (float) maxScroll);
            graphics.fill(trackX, this.bodyTop, trackX + 2, this.bodyTop + this.bodyHeight, CrystalTheme.SCROLL_TRACK);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, CrystalTheme.SCROLL_THUMB);
        }

        float buttonsFade = 0.35F + 0.65F * enter;
        this.understood.setAlpha(buttonsFade);
        this.dismiss.setAlpha(buttonsFade);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, this.bodyContentHeight - this.bodyHeight);
        if (maxScroll > 0 && verticalAmount != 0.0D) {
            this.bodyScroll = Mth.clamp(this.bodyScroll - (int) Math.signum(verticalAmount) * (this.font.lineHeight + 1) * 2,
                    0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        proceed();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void proceed() {
        this.minecraft.setScreen(this.next);
    }

    private void dismissForever() {
        CrystalVisualConfig.setOptimizerNoticeDismissed(true);
        CrystalVisualConfig.save();
        proceed();
    }
}
