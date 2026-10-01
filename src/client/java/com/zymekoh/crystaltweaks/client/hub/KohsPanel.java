package com.zymekoh.crystaltweaks.client.hub;

import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.hub.HubLayout.Rect;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * The KoHs tab: Zymekoh, who makes Crystal Tweaks, as the KoHs Mod Suite site draws her (a hooded
 * figure with glowing eyes and a code sigil), and below her the name, what she does, and links to
 * Discord, the site, Modrinth and Buy me a coffee. Taken from KoHs Anchor's and fitted to the hub.
 *
 * <p>The drawing is the site's own, in layers that move as they do there: the sigil's ring and
 * runes turn opposite ways, the eyes blink and flare, a glint runs down the blade of light, the
 * shards drift, and the figure follows the pointer with a slight parallax. A click on her lights
 * everything up. Every link opens through Minecraft's own confirmation screen.</p>
 */
final class KohsPanel {
    static final String DISCORD_URL = "https://discord.gg/9t2VxEF7UU";
    static final String SITE_URL = "https://kerlycanelita.github.io/KoHs-Mod-Suite/";
    static final String MODRINTH_URL = "https://modrinth.com/user/zymery_dria";
    static final String COFFEE_URL = "https://buymeacoffee.com/zymekohh";

    private static final float ART_WIDTH = 400.0F;
    private static final float ART_TOP = 10.0F;
    private static final float ART_HEIGHT = 450.0F;
    private static final long FLARE_NANOS = 900_000_000L;

    private record Layer(Identifier texture, int textureWidth, int textureHeight, float x, float y, float width, float height) {
        float centerX() {
            return this.x + this.width / 2.0F;
        }

        float centerY() {
            return this.y + this.height / 2.0F;
        }
    }

    private static final Layer AURA = layer("aura", 192, 192, 10, 6, 380, 380);
    private static final Layer SIGIL_RING = layer("sigil_ring", 448, 448, 25, 21, 350, 350);
    private static final Layer SIGIL_RUNES = layer("sigil_runes", 448, 448, 25, 21, 350, 350);
    private static final Layer SLASH = layer("slash", 496, 563, 12, 10, 388, 440);
    private static final Layer FIGURE = layer("figure", 512, 563, 0, 20, 400, 440);
    private static final Layer EYES = layer("eyes", 179, 76, 130, 160, 140, 60);
    private static final Layer EMBLEM = layer("emblem", 128, 128, 150, 332, 100, 100);
    private static final Layer[] SHARDS = {
            layer("shard0", 71, 71, 31, 91, 56, 56), layer("shard1", 71, 71, 316, 71, 56, 56),
            layer("shard2", 71, 71, 305, 233, 56, 56), layer("shard3", 71, 71, 41, 235, 56, 56),
            layer("shard4", 71, 71, 77, 31, 56, 56), layer("shard5", 71, 71, 277, 20, 56, 56)};
    private static final float[] SHARD_PERIOD = {7.0F, 8.0F, 7.0F, 9.0F, 7.0F, 6.0F};
    private static final float[] SHARD_DELAY = {0.0F, 1.2F, 2.4F, 3.1F, 4.3F, 5.5F};
    private static final Identifier DISCORD_ICON = texture("discord");
    private static final Identifier KOHS_MARK = texture("kohs_mark");
    private static final Identifier MODRINTH_MARK = texture("modrinth_mark");
    private static final Identifier MODRINTH_OUTER = texture("modrinth_outer");
    private static final Identifier MODRINTH_INNER = texture("modrinth_inner");
    private static final Identifier COFFEE = texture("coffee");
    private static final float SLASH_X0 = 387.5F;
    private static final float SLASH_Y0 = 23.5F;
    private static final float SLASH_X1 = 28.5F;
    private static final float SLASH_Y1 = 437.5F;
    private static final String FINALE = "KOHS ON TOP";
    private static final int[] FINALE_COLORS = {0xFFFF4FB8, 0xFFE83EAF, 0xFFC084FC, 0xFFA855F7, 0xFFD8B4FE};
    private static final int[] BUTTON_GLOWS = {0xFF5865F2, 0xFFFF4FB8, 0xFF1BD96A, 0xFFFFC85A};

    private final Screen screen;
    private final boolean spanish;
    private long enteredAt = System.nanoTime();
    private long flareAt = -1L;
    private final float[] buttonHover = new float[4];
    private final float[] chipHover = new float[6];
    private float artHover;
    private float parallaxX;
    private float parallaxY;

    KohsPanel(Screen screen, boolean spanish) {
        this.screen = screen;
        this.spanish = spanish;
    }

    /** Also used by the carousel for this tab's icon. */
    static Identifier mark() {
        return KOHS_MARK;
    }

    private static Identifier texture(String name) {
        return HubDraw.own("textures/gui/kohs/" + name + ".png");
    }

    private static Layer layer(String name, int textureWidth, int textureHeight, float x, float y, float width, float height) {
        return new Layer(texture(name), textureWidth, textureHeight, x, y, width, height);
    }

    void enter() {
        this.enteredAt = System.nanoTime();
        this.flareAt = -1L;
        play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3F, 0.45F);
    }

    // ------------------------------------------------------------------------------------------
    // Words
    // ------------------------------------------------------------------------------------------

    private String eyebrow() {
        return this.spanish ? "La creadora" : "The creator";
    }

    private String aka() {
        return this.spanish ? "alias" : "a.k.a.";
    }

    private String role() {
        return this.spanish
                ? "KoHs Mod Suite es un estudio de una sola persona: cada mod y plugin, su código, pantallas, arte y actualizaciones, lo hace Zymekoh. Herramientas para un PvP de Minecraft más preciso y más vistoso, hechas para el juego limpio."
                : "KoHs Mod Suite is a one-person studio: every mod and plugin, its code, screens, art and updates, is made by Zymekoh. Tools that make Minecraft PvP sharper and better looking, built for fair play.";
    }

    private String roleShort() {
        return this.spanish
                ? "Cada mod y plugin de KoHs, su código, arte y actualizaciones, hecho por una sola persona para un PvP limpio."
                : "Every KoHs mod and plugin, its code, art and updates, made by one person for fair PvP.";
    }

    private String skill(int index) {
        String[] spanishSkills = {"Servidores avanzados", "Plugins Paper y Spigot", "Puentes mod ↔ plugin", "Mods de Fabric",
                "BungeeCord y Velocity", "Auditorías de servidores"};
        String[] englishSkills = {"Advanced servers", "Paper & Spigot plugins", "Mod ↔ plugin bridges", "Fabric mods",
                "BungeeCord & Velocity", "Server audits"};
        return this.spanish ? spanishSkills[index] : englishSkills[index];
    }

    private String[] buttonLabels() {
        return this.spanish
                ? new String[] {"Discord", "Página web", "Modrinth", "Invítame un café"}
                : new String[] {"Discord", "Website", "Modrinth", "Buy me a coffee"};
    }

    // ------------------------------------------------------------------------------------------
    // Arrangement
    // ------------------------------------------------------------------------------------------

    private record Parts(Rect art, float scale, boolean compact, int eyebrowY, int nameY, float nameScale, int akaY,
            List<FormattedCharSequence> role, int roleY, Rect[] buttons, int finaleY, float finaleScale, Rect[] chips) {
    }

    private Parts parts(Font font, Rect body) {
        boolean compact = body.height() < 210 || body.width() < 300;
        boolean tall = body.height() >= 260;
        int eyebrow = compact ? 0 : 12;
        String name = "Zymekoh";
        float nameScale = Math.min(compact ? 1.6F : tall ? 2.2F : 2.0F, (body.width() - 12) / (float) Math.max(1, font.width(name)));
        int nameHeight = Math.round(9 * nameScale) + 3;
        int akaHeight = 12;
        int textWidth = Math.min(body.width() - 16, 440);
        List<FormattedCharSequence> role = CrystalUi.wrap(font, role(), Math.max(60, textWidth));
        int buttonHeight = compact ? 14 : 16;
        String[] labels = buttonLabels();
        int gap = 5;
        int[] widths = new int[4];
        int rowWidth = 0;
        for (int index = 0; index < 4; index++) {
            widths[index] = font.width(labels[index]) + buttonHeight + 12;
            rowWidth += widths[index];
        }
        rowWidth += gap * 3;
        boolean twoRows = rowWidth > body.width() - 8;
        int buttonsHeight = twoRows ? buttonHeight * 2 + 4 : buttonHeight;
        float finaleScale = Math.min(compact ? 1.3F : 1.8F, (body.width() - 12) / (float) Math.max(1, finaleWidth(font, 1.0F)));
        int finaleHeight = Math.round(9 * finaleScale) + 2;
        int maxRole = tall ? 3 : 2;
        int overlap = eyebrow > 0 ? 6 : 0;
        int artHeight;
        while (true) {
            int roleLines = Math.min(maxRole, role.size());
            int text = eyebrow + nameHeight + akaHeight + roleLines * 10 + 5 + buttonsHeight + 6 + finaleHeight;
            artHeight = Math.min(compact ? 100 : 180, body.height() - text - 6 + overlap);
            if (artHeight >= 52 || maxRole <= 1) {
                break;
            }
            maxRole--;
        }
        if (artHeight < 52) {
            artHeight = 0;
        }
        if (role.size() > maxRole) {
            List<FormattedCharSequence> brief = CrystalUi.wrap(font, roleShort(), Math.max(60, textWidth));
            role = brief.size() <= maxRole ? brief : brief.subList(0, maxRole);
        }
        int artWidth = Math.round(artHeight * ART_WIDTH / ART_HEIGHT);
        int artSpace = artHeight > 0 ? artHeight + 4 - overlap : 0;
        int total = artSpace + eyebrow + nameHeight + akaHeight + role.size() * 10 + 5 + buttonsHeight + 6 + finaleHeight;
        int y = body.y() + Math.max(0, (body.height() - total) / 2);
        Rect art = artHeight > 0 ? new Rect(body.centerX() - artWidth / 2, y, artWidth, artHeight) : Rect.EMPTY;
        y += artSpace;
        int eyebrowY = y;
        y += eyebrow;
        int nameY = y;
        y += nameHeight;
        int akaY = y;
        y += akaHeight;
        int roleY = y;
        y += role.size() * 10 + 5;
        Rect[] buttons = new Rect[4];
        if (twoRows) {
            int each = Math.max(40, (Math.min(body.width() - 8, 300) - gap) / 2);
            int left = body.centerX() - (each * 2 + gap) / 2;
            for (int index = 0; index < 4; index++) {
                int column = index % 2;
                int row = index / 2;
                buttons[index] = new Rect(left + column * (each + gap), y + row * (buttonHeight + 4), each, buttonHeight);
            }
        } else {
            int x = body.centerX() - rowWidth / 2;
            for (int index = 0; index < 4; index++) {
                buttons[index] = new Rect(x, y, widths[index], buttonHeight);
                x += widths[index] + gap;
            }
        }
        y += buttonsHeight + 6;
        int finaleY = y;
        Rect[] chips = new Rect[0];
        int side = (body.width() - artWidth) / 2 - 16;
        if (artHeight >= 80 && side >= 104) {
            chips = new Rect[6];
            int chipWidth = Math.min(side, 140);
            int chipHeight = 15;
            int chipGap = Math.min(10, Math.max(4, (artHeight - chipHeight * 3) / 4));
            int top = art.y() + (artHeight - chipHeight * 3 - chipGap * 2) / 2;
            for (int index = 0; index < 3; index++) {
                int chipY = top + index * (chipHeight + chipGap);
                chips[index] = new Rect(art.x() - 12 - chipWidth, chipY, chipWidth, chipHeight);
                chips[index + 3] = new Rect(art.right() + 12, chipY, chipWidth, chipHeight);
            }
        }
        return new Parts(art, artHeight / ART_HEIGHT, compact, eyebrowY, nameY, nameScale, akaY, role, roleY, buttons, finaleY,
                finaleScale, chips);
    }

    // ------------------------------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------------------------------

    boolean mouseClicked(Font font, Rect body, double mouseX, double mouseY) {
        Parts parts = parts(font, body);
        String[] urls = {DISCORD_URL, SITE_URL, MODRINTH_URL, COFFEE_URL};
        for (int index = 0; index < 4; index++) {
            if (parts.buttons()[index].contains(mouseX, mouseY)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ConfirmLinkScreen.confirmLinkNow(this.screen, URI.create(urls[index]));
                return true;
            }
        }
        if (parts.art().contains(mouseX, mouseY)) {
            this.flareAt = System.nanoTime();
            play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.9F);
            return true;
        }
        return false;
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------------------------------

    void render(GuiGraphicsExtractor graphics, Font font, Rect body, int mouseX, int mouseY, float intro, float frameMillis) {
        long now = System.nanoTime();
        float response = 1.0F - (float) Math.exp(-frameMillis / 70.0F);
        double seconds = now / 1_000_000_000.0D;
        float since = (now - this.enteredAt) / 1_000_000_000.0F;
        Parts parts = parts(font, body);
        Rect art = parts.art();
        boolean overArt = art.contains(mouseX, mouseY);
        this.artHover += ((overArt ? 1.0F : 0.0F) - this.artHover) * response;
        float targetX = 0.0F;
        float targetY = 0.0F;
        if (art.width() > 0 && body.contains(mouseX, mouseY)) {
            targetX = HubMotion.clamp01((mouseX - body.x()) / (float) Math.max(1, body.width())) * 2.0F - 1.0F;
            targetY = HubMotion.clamp01((mouseY - body.y()) / (float) Math.max(1, body.height())) * 2.0F - 1.0F;
        }
        this.parallaxX += (targetX - this.parallaxX) * response * 0.5F;
        this.parallaxY += (targetY - this.parallaxY) * response * 0.5F;
        if (art.width() > 0) {
            drawArt(graphics, parts, seconds, since, intro, now);
        }
        drawChips(graphics, font, parts, mouseX, mouseY, since, intro, response, seconds);
        drawText(graphics, font, body, parts, since, intro, seconds);
        drawButtons(graphics, font, parts, mouseX, mouseY, since, intro, response, seconds);
        drawFinale(graphics, font, body, parts, since, intro, seconds);
    }

    private static float appear(float since, float start, float length) {
        return HubMotion.easeOutCubic(HubMotion.clamp01((since - start) / length));
    }

    private void drawArt(GuiGraphicsExtractor graphics, Parts parts, double time, float since, float intro, long now) {
        Rect art = parts.art();
        float scale = parts.scale();
        float originX = art.x();
        float originY = art.y() - ART_TOP * scale;
        float flare = this.flareAt < 0L ? 0.0F : 1.0F - HubMotion.clamp01((now - this.flareAt) / (float) FLARE_NANOS);
        float glow = Math.max(flare, 0.35F * this.artHover);
        HubDraw.isolate(graphics);
        graphics.enableScissor(art.x() - 2, art.y() - 2, art.right() + 2, art.bottom());
        float auraIn = appear(since, 0.0F, 0.6F);
        float breathe = 0.84F + 0.16F * (float) Math.sin(time * Math.PI * 2.0D / 4.0D);
        float auraScale = 0.86F + 0.14F * auraIn + 0.02F * (float) Math.sin(time * 1.3D);
        drawLayer(graphics, AURA, originX, originY, scale, 0.6F, 0.0F, auraScale,
                CrystalTheme.fade(0xFFFFFFFF, intro * auraIn * Math.min(1.0F, breathe + glow * 0.3F)));
        float sigilIn = appear(since, 0.08F, 0.8F);
        double burst = (1.0D - sigilIn) * Math.PI * 0.9D;
        float sigilScale = 1.14F - 0.14F * sigilIn;
        drawLayer(graphics, SIGIL_RING, originX, originY, scale, 1.2F, (float) (time * Math.PI * 2.0D / 80.0D + burst), sigilScale,
                CrystalTheme.fade(0xFFFFFFFF, intro * sigilIn));
        drawLayer(graphics, SIGIL_RUNES, originX, originY, scale, 1.6F, (float) (-time * Math.PI * 2.0D / 120.0D - burst), sigilScale,
                CrystalTheme.fade(0xFFFFFFFF, intro * sigilIn * (0.8F + 0.2F * glow)));
        if (flare > 0.0F) {
            float ring = 1.0F - flare;
            HubDraw.ring(graphics, originX + 200 * scale, originY + 196 * scale, (150 + 60 * ring) * scale, 2,
                    CrystalTheme.withAlpha(0xFF4FB8, Math.round(200 * flare)));
        }
        float slashIn = appear(since, 0.25F, 0.35F);
        float slashPulse = 0.65F + 0.2F * (float) Math.sin(time * Math.PI * 2.0D / 6.0D);
        drawLayer(graphics, SLASH, originX, originY, scale, 2.2F, 0.0F, 1.0F,
                CrystalTheme.fade(0xFFFFFFFF, intro * slashIn * Math.min(1.0F, slashPulse + glow)));
        float figureIn = appear(since, 0.12F, 0.7F);
        float bob = (float) Math.sin(time * Math.PI * 2.0D / 5.0D) * 1.2F;
        float figureY = originY + ((1.0F - figureIn) * 14.0F + bob) * scale;
        drawLayer(graphics, FIGURE, originX, figureY, scale, 3.0F, 0.0F, 1.0F, CrystalTheme.fade(0xFFFFFFFF, intro * figureIn));
        float eyesIn = appear(since, 0.55F, 0.25F);
        float blink = 1.0F;
        double phase = (time % 5.0D) / 5.0D;
        if (phase > 0.46D && phase < 0.5D) {
            blink = phase < 0.48D ? (float) (1.0D - (phase - 0.46D) / 0.02D * 0.85D) : (float) (0.15D + (phase - 0.48D) / 0.02D * 0.85D);
        }
        float eyes = intro * eyesIn * blink;
        drawLayer(graphics, EYES, originX, figureY, scale, 3.0F, 0.0F, 1.0F, CrystalTheme.fade(0xFFFFFFFF, eyes));
        float ignite = Math.max(0.0F, 1.0F - Math.abs(since - 0.7F) / 0.2F);
        if (glow > 0.02F || ignite > 0.02F) {
            drawLayer(graphics, EYES, originX, figureY, scale, 3.0F, 0.0F, 1.0F + 0.08F * Math.max(glow, ignite),
                    CrystalTheme.fade(0xFFFFFFFF, eyes * Math.max(glow, ignite)));
        }
        float emblemPulse = 0.86F + 0.14F * (float) Math.sin(time * Math.PI * 2.0D / 3.2D);
        drawLayer(graphics, EMBLEM, originX, figureY, scale, 3.0F, 0.0F, 0.98F + 0.04F * emblemPulse,
                CrystalTheme.fade(0xFFFFFFFF, intro * figureIn * emblemPulse));
        for (int index = 0; index < SHARDS.length; index++) {
            float shardIn = appear(since, 0.3F + index * 0.06F, 0.4F);
            double shardPhase = (time + SHARD_DELAY[index]) / SHARD_PERIOD[index];
            float drift = (float) (0.5D - 0.5D * Math.cos(shardPhase * Math.PI * 2.0D));
            drawLayer(graphics, SHARDS[index], originX, originY - drift * 10.0F * scale, scale, 2.6F,
                    (float) Math.toRadians(14.0D * drift), 1.0F + 0.3F * flare, CrystalTheme.fade(0xFFFFFFFF, intro * shardIn));
        }
        graphics.disableScissor();
        HubDraw.isolate(graphics);
        glint(graphics, originX, originY, scale, time, flare, intro * slashIn);
        sparks(graphics, art, time, intro * auraIn);
        HubDraw.isolate(graphics);
    }

    private void drawLayer(GuiGraphicsExtractor graphics, Layer layer, float originX, float originY, float scale, float depth,
            float angle, float grow, int color) {
        float centerX = originX + (layer.centerX() + this.parallaxX * depth) * scale;
        float centerY = originY + (layer.centerY() + this.parallaxY * depth * 0.6F) * scale;
        HubDraw.texture(graphics, layer.texture(), layer.textureWidth(), layer.textureHeight(), centerX, centerY,
                layer.width() * scale * grow, layer.height() * scale * grow, angle, color);
    }

    private void glint(GuiGraphicsExtractor graphics, float originX, float originY, float scale, double time, float flare,
            float alpha) {
        double period = 4.5D;
        double run = 0.55D;
        double phase = time % period;
        float t = flare > 0.0F ? 1.0F - flare : (float) (phase / run);
        if (t < 0.0F || t > 1.0F || alpha <= 0.05F) {
            return;
        }
        float eased = HubMotion.easeInOutSine(t);
        for (int step = 0; step < 10; step++) {
            float s = eased - step * 0.012F;
            if (s < 0.0F) {
                break;
            }
            float ux = SLASH_X0 + (SLASH_X1 - SLASH_X0) * s + this.parallaxX * 2.2F;
            float uy = SLASH_Y0 + (SLASH_Y1 - SLASH_Y0) * s + this.parallaxY * 1.3F;
            int x = Math.round(originX + ux * scale);
            int y = Math.round(originY + uy * scale);
            int size = step == 0 ? 3 : step < 4 ? 2 : 1;
            int tone = CrystalTheme.withAlpha(step == 0 ? 0xFFFFFF : 0xFFD1EC, Math.round(255 * alpha * (1.0F - step / 10.0F)));
            graphics.fill(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size, tone);
        }
    }

    private static void sparks(GuiGraphicsExtractor graphics, Rect art, double time, float alpha) {
        if (alpha <= 0.05F) {
            return;
        }
        int span = art.height() + 10;
        for (int index = 0; index < 14; index++) {
            double speed = 9.0D + index % 5 * 3.0D;
            double phase = index * 0.618D;
            int x = art.x() + Math.floorMod(index * 53 + 17, Math.max(1, art.width()))
                    + (int) Math.round(Math.sin(time * 0.9D + phase * 5.0D) * 3.0D);
            int y = art.bottom() - (int) ((time * speed + phase * span) % span);
            float life = 1.0F - (art.bottom() - y) / (float) span;
            int color = index % 3 == 0 ? 0xFF4FB8 : index % 3 == 1 ? 0xC084FC : 0xF5D0FE;
            int size = index % 4 == 0 ? 2 : 1;
            graphics.fill(x, y, x + size, y + size, CrystalTheme.withAlpha(color, Math.round(200 * alpha * life)));
        }
    }

    private void drawChips(GuiGraphicsExtractor graphics, Font font, Parts parts, int mouseX, int mouseY, float since,
            float intro, float response, double seconds) {
        Rect[] chips = parts.chips();
        for (int index = 0; index < chips.length; index++) {
            Rect chip = chips[index];
            boolean left = index < 3;
            float in = appear(since, 0.35F + (index % 3) * 0.08F, 0.45F);
            if (in <= 0.02F) {
                continue;
            }
            this.chipHover[index] += ((chip.contains(mouseX, mouseY) ? 1.0F : 0.0F) - this.chipHover[index]) * response;
            float hover = this.chipHover[index];
            int slide = Math.round((1.0F - in) * 18.0F) * (left ? -1 : 1);
            int x = chip.x() + slide;
            float alpha = intro * in;
            HubDraw.glass(graphics, x, chip.y(), chip.width(), chip.height(),
                    CrystalTheme.lerp(0x901D0D32, 0xC02A1248, hover), 0x8012091F,
                    CrystalTheme.lerp(0x9A6A2A9A, 0xE0FF8AD8, hover), alpha);
            double scan = ((seconds * 0.35D) + index / 6.0D) % 1.0D;
            int scanX = x + (int) Math.round(scan * (chip.width() + 20)) - 10;
            graphics.enableScissor(x + 1, chip.y() + 1, x + chip.width() - 1, chip.bottom() - 1);
            graphics.fill(scanX, chip.y() + 1, scanX + 2, chip.bottom() - 1, CrystalTheme.withAlpha(0xF5D0FE, Math.round(30 * alpha)));
            graphics.disableScissor();
            int diamondX = left ? x + chip.width() - 8 : x + 7;
            HubDraw.diamond(graphics, diamondX, chip.centerY(), 2, CrystalTheme.fade(0xFFFF4FB8, alpha));
            String label = HubDraw.fit(font, skill(index), chip.width() - 20);
            int textX = left ? x + chip.width() - 14 - font.width(label) : x + 14;
            CrystalUi.label(graphics, font, label, textX, chip.y() + (chip.height() - 8) / 2,
                    CrystalTheme.fade(CrystalTheme.lerp(0xFFC6B5CC, 0xFFE9D5FF, hover), alpha));
        }
    }

    private void drawText(GuiGraphicsExtractor graphics, Font font, Rect body, Parts parts, float since, float intro,
            double seconds) {
        int centerX = body.centerX();
        if (!parts.compact()) {
            float in = appear(since, 0.3F, 0.35F);
            String eyebrow = "◆ " + eyebrow().toUpperCase(Locale.ROOT);
            CrystalUi.label(graphics, font, eyebrow, centerX - font.width(eyebrow) / 2, parts.eyebrowY() + Math.round((1.0F - in) * 4),
                    CrystalTheme.fade(0xFFFF8AD8, intro * in));
        }
        String name = "ZYMEKOH";
        float decode = appear(since, 0.35F, 0.55F);
        String shown = name;
        if (decode < 1.0F) {
            String signs = "<>/\\#*+=ZYMEKOHS";
            StringBuilder scrambled = new StringBuilder(name.length());
            long tick = (long) (since * 30.0F);
            for (int index = 0; index < name.length(); index++) {
                boolean settled = decode > (index + 1) / (float) (name.length() + 1);
                scrambled.append(settled ? name.charAt(index) : signs.charAt((int) Math.floorMod(tick * 31 + index * 17L, signs.length())));
            }
            shown = scrambled.toString();
        }
        float nameIn = appear(since, 0.35F, 0.3F);
        HubDraw.bigText(graphics, font, shown, centerX + 1, parts.nameY() + 1, parts.nameScale(), CrystalTheme.fade(0xFF6B0F5A, intro * nameIn), false);
        HubDraw.bigText(graphics, font, shown, centerX, parts.nameY(), parts.nameScale(), CrystalTheme.fade(0xFFE9D5FF, intro * nameIn), false);
        float akaIn = appear(since, 0.55F, 0.35F);
        String prefix = aka() + " ";
        String first = "kohze";
        String second = "myora";
        int width = font.width(prefix + first + second);
        int x = centerX - width / 2;
        int y = parts.akaY() + Math.round((1.0F - akaIn) * 4);
        CrystalUi.label(graphics, font, prefix, x, y, CrystalTheme.fade(0xFF9C86AA, intro * akaIn));
        x += font.width(prefix);
        CrystalUi.label(graphics, font, first, x, y, CrystalTheme.fade(0xFFFF8AD8, intro * akaIn));
        x += font.width(first);
        CrystalUi.label(graphics, font, second, x, y, CrystalTheme.fade(0xFFC084FC, intro * akaIn));
        int lineY = parts.roleY();
        for (int index = 0; index < parts.role().size(); index++) {
            float in = appear(since, 0.65F + index * 0.07F, 0.35F);
            FormattedCharSequence line = parts.role().get(index);
            HubDraw.line(graphics, font, line, centerX - font.width(line) / 2, lineY + Math.round((1.0F - in) * 4),
                    CrystalTheme.fade(0xFFC6B5CC, intro * in));
            lineY += 10;
        }
    }

    private void drawButtons(GuiGraphicsExtractor graphics, Font font, Parts parts, int mouseX, int mouseY, float since,
            float intro, float response, double seconds) {
        String[] labels = buttonLabels();
        for (int index = 0; index < 4; index++) {
            Rect button = parts.buttons()[index];
            float in = appear(since, 0.8F + index * 0.07F, 0.35F);
            if (in <= 0.02F) {
                continue;
            }
            this.buttonHover[index] += ((button.contains(mouseX, mouseY) ? 1.0F : 0.0F) - this.buttonHover[index]) * response;
            float hover = this.buttonHover[index];
            float alpha = intro * in;
            if (hover > 0.05F) {
                HubDraw.halo(graphics, button.x(), button.y(), button.width(), button.height(), BUTTON_GLOWS[index], 3, hover * alpha);
            }
            int top = CrystalTheme.lerp(0xC03A1748, 0xE05D2877, hover);
            if (index == 3) {
                // Buy me a coffee wears warm gold over the purple.
                top = CrystalTheme.lerp(0xC0503012, 0xE0805018, hover);
            }
            HubDraw.glass(graphics, button.x(), button.y(), button.width(), button.height(), top, HubSkin.darken(top, 0.55F),
                    CrystalTheme.lerp(index == 3 ? 0xC0C89040 : 0xC08B50B5, BUTTON_GLOWS[index], hover), alpha);
            int icon = button.height() - 5;
            String label = HubDraw.fit(font, labels[index], button.width() - icon - 10);
            int contentWidth = icon + 4 + font.width(label);
            int x = button.x() + (button.width() - contentWidth) / 2;
            float iconCenterX = x + icon / 2.0F;
            float iconCenterY = button.y() + 2.5F + icon / 2.0F;
            switch (index) {
                case 0 -> HubDraw.texture(graphics, DISCORD_ICON, 64, 64, iconCenterX, iconCenterY, icon, icon, 0.0F,
                        CrystalTheme.fade(CrystalTheme.lerp(0xFFB4BBFF, 0xFFFFFFFF, hover), alpha));
                case 1 -> HubDraw.texture(graphics, KOHS_MARK, 96, 96, iconCenterX, iconCenterY, icon, icon, 0.0F,
                        CrystalTheme.fade(0xFFFFFFFF, alpha));
                case 2 -> {
                    int green = CrystalTheme.fade(CrystalTheme.lerp(0xFF1BD96A, 0xFF9CFFC6, hover), alpha);
                    float turn = (float) (seconds / 4.0D * Math.PI * 2.0D) * (0.3F + hover);
                    HubDraw.texture(graphics, MODRINTH_OUTER, 256, 256, iconCenterX, iconCenterY, icon, icon, turn, green);
                    HubDraw.texture(graphics, MODRINTH_INNER, 256, 256, iconCenterX, iconCenterY, icon, icon, -turn * 0.66F, green);
                    HubDraw.texture(graphics, MODRINTH_MARK, 256, 256, iconCenterX, iconCenterY, icon, icon, 0.0F, green);
                }
                default -> {
                    // The cup rocks a little and steams.
                    float rock = (float) Math.sin(seconds * 2.4D) * 0.08F * (0.4F + hover);
                    HubDraw.texture(graphics, COFFEE, 32, 32, iconCenterX, iconCenterY, icon, icon, rock, CrystalTheme.fade(0xFFFFFFFF, alpha));
                    for (int puff = 0; puff < 3; puff++) {
                        double rise = (seconds * 0.9D + puff / 3.0D) % 1.0D;
                        int px = Math.round(iconCenterX - 2 + puff * 2 + (float) Math.sin(seconds * 3.0D + puff) * 1.2F);
                        int py = Math.round(button.y() + 1 - (float) rise * 5.0F);
                        graphics.fill(px, py, px + 1, py + 1, CrystalTheme.withAlpha(0xFFF4E6, Math.round(160 * (1.0F - (float) rise) * alpha)));
                    }
                }
            }
            CrystalUi.label(graphics, font, label, x + icon + 4, button.y() + (button.height() - 8) / 2,
                    CrystalTheme.fade(CrystalTheme.lerp(0xFFF7EDFF, 0xFFFFFFFF, hover), alpha), true);
        }
    }

    private static int finaleWidth(Font font, float scale) {
        int width = 0;
        for (int index = 0; index < FINALE.length(); index++) {
            width += font.width(String.valueOf(FINALE.charAt(index))) + 1;
        }
        return Math.round((width - 1) * scale);
    }

    private void drawFinale(GuiGraphicsExtractor graphics, Font font, Rect body, Parts parts, float since, float intro,
            double time) {
        float scale = parts.finaleScale();
        int total = finaleWidth(font, scale);
        float x = body.centerX() - total / 2.0F;
        int y = parts.finaleY();
        double glitchPhase = time % 3.4D;
        boolean glitch = glitchPhase < 0.14D && since > 1.6F;
        float jitter = glitch ? (float) Math.sin(time * 90.0D) * 1.5F : 0.0F;
        HubDraw.isolate(graphics);
        for (int index = 0; index < FINALE.length(); index++) {
            String letter = String.valueOf(FINALE.charAt(index));
            float in = appear(since, 0.95F + index * 0.045F, 0.3F);
            if (letter.equals(" ") || in <= 0.02F) {
                x += (font.width(letter) + 1) * scale;
                continue;
            }
            float wave = (float) Math.sin(time * 3.2D - index * 0.55D) * 1.4F;
            float rise = (1.0F - in) * 8.0F;
            float shift = (float) ((time * 0.25D + index / (double) FINALE.length()) % 1.0D);
            int color = gradient(shift);
            float alpha = intro * in;
            float letterY = y + wave + rise;
            if (glitch) {
                drawLetter(graphics, font, letter, x - 1.5F + jitter, letterY, scale, CrystalTheme.fade(0xB0FF4FB8, alpha));
                drawLetter(graphics, font, letter, x + 1.5F - jitter, letterY, scale, CrystalTheme.fade(0xB052F2FF, alpha));
            }
            drawLetter(graphics, font, letter, x + 1.0F, letterY + 1.0F, scale, CrystalTheme.fade(0xFF3B0A5A, alpha));
            drawLetter(graphics, font, letter, x, letterY, scale, CrystalTheme.fade(color, alpha));
            x += (font.width(letter) + 1) * scale;
        }
        HubDraw.isolate(graphics);
    }

    private static void drawLetter(GuiGraphicsExtractor graphics, Font font, String letter, float x, float y, float scale, int color) {
        if (((color >>> 24) & 255) < 8) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, letter, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    private static int gradient(float position) {
        float scaled = position * FINALE_COLORS.length;
        int from = (int) Math.floor(scaled) % FINALE_COLORS.length;
        int to = (from + 1) % FINALE_COLORS.length;
        return CrystalTheme.lerp(FINALE_COLORS[from], FINALE_COLORS[to], scaled - (float) Math.floor(scaled));
    }
}
