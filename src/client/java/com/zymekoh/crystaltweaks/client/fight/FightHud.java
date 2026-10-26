package com.zymekoh.crystaltweaks.client.fight;

import com.zymekoh.crystaltweaks.client.CrystalPlacementFeedback;
import com.zymekoh.crystaltweaks.client.CrystalTheme;
import com.zymekoh.crystaltweaks.client.CrystalUi;
import com.zymekoh.crystaltweaks.client.CrystalVisualConfig;
import com.zymekoh.crystaltweaks.client.hub.HubDraw;
import com.zymekoh.crystaltweaks.client.practice.PracticeHost;
import com.zymekoh.crystaltweaks.core.CrystalBreakPrediction;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/**
 * Crystal Tweaks' help in a real fight, drawn with the HUD: why a placement did not happen, under
 * the crosshair, and the crystal round trip beside the hotbar; and the recap on the death screen.
 * Drawing only: none of it touches input, the world or the network.
 */
public final class FightHud {
    private static boolean initialized;
    /** The latency meter's text, rebuilt only when a number changes. */
    private static String meter = "";
    private static long meterKey = Long.MIN_VALUE;

    private FightHud() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("crystal_tweaks", "fight"), (graphics, deltaTracker) -> draw(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (PracticeHost.serverSide()) {
                return;
            }
            long now = System.nanoTime();
            PlacementDiagnostic.tick(now);
            DeathRecap.tick(client.player, now);
        });
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            PlacementDiagnostic.reset();
            DeathRecap.reset();
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof DeathScreen) {
                // The screen can open before the tick sees the death: the recap is built here then, and
                // it may still take the killing hit in, so each frame draws it as it is.
                DeathRecap.forDeathScreen(client.player, System.nanoTime());
                ScreenEvents.afterExtract(screen).register((shown, graphics, mouseX, mouseY, delta) -> {
                    List<String> lines = DeathRecap.screenLines();
                    if (!lines.isEmpty()) {
                        drawRecap(graphics, lines);
                    }
                });
            }
        });
    }

    private static void draw(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        long now = System.nanoTime();
        Font font = minecraft.font;
        boolean spanish = CrystalUi.spanish();
        PlacementDiagnostic.Reason reason = PlacementDiagnostic.shown(now);
        if (reason != null) {
            float age = PlacementDiagnostic.age(now) / (float) PlacementDiagnostic.SHOW_NANOS;
            float alpha = Math.min(1.0F, Math.min(age * 8.0F, (1.0F - age) * 3.0F));
            String text = reason.text(spanish);
            String hint = reason.hint(spanish);
            int width = Math.max(font.width(text), font.width(hint)) + 12;
            int height = hint.isEmpty() ? 13 : 23;
            int x = (graphics.guiWidth() - width) / 2;
            int y = graphics.guiHeight() / 2 + 12 + Math.round((1.0F - Math.min(1.0F, age * 6.0F)) * 4.0F);
            int danger = reason == PlacementDiagnostic.Reason.SPENT ? 0xFFC880FF : 0xFFFF315C;
            HubDraw.glass(graphics, x, y, width, height, 0xD0160A22, 0xD00C0612, danger, alpha);
            graphics.text(font, text, x + 6, y + 3, CrystalTheme.fade(0xFFF4EEFA, alpha), true);
            if (!hint.isEmpty()) {
                graphics.text(font, hint, x + 6, y + 13, CrystalTheme.fade(0xFFC6B5CC, alpha), true);
            }
        }
        if (CrystalVisualConfig.latencyMeter() && holdsCrystalGear(player)) {
            drawMeter(graphics, font, spanish, now);
        }
    }

    private static boolean holdsCrystalGear(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            if (stack.is(Items.END_CRYSTAL) || stack.is(Items.OBSIDIAN)) {
                return true;
            }
        }
        return false;
    }

    /** The place and break round trips, right of the hotbar, with a light that says how they feel. */
    private static void drawMeter(GuiGraphicsExtractor graphics, Font font, boolean spanish, long now) {
        long place = CrystalPlacementFeedback.lastLatencyMillis();
        long hit = CrystalBreakPrediction.breakLatencyMillis();
        long key = place * 100_003L + hit;
        if (key != meterKey) {
            meterKey = key;
            meter = (spanish ? "Poner " : "Place ") + (place < 0 ? "—" : place + " ms") + (spanish ? " · Romper " : " · Break ")
                    + (hit < 0 ? "—" : hit + " ms");
        }
        long worst = Math.max(place, hit);
        int light = worst < 0 ? 0xFF8E8AA0 : worst < 80 ? 0xFFC880FF : worst < 150 ? 0xFFFFC48A : 0xFFFF315C;
        int x = graphics.guiWidth() / 2 + 98;
        int y = graphics.guiHeight() - 17;
        float pulse = 0.6F + 0.4F * (float) Math.sin(now / 1_000_000_000.0D * 3.0D);
        HubDraw.glowDisc(graphics, x + 3, y + 4, 5, light, 0.55F * pulse);
        graphics.fill(x + 2, y + 3, x + 5, y + 6, light);
        graphics.text(font, meter, x + 9, y, 0xFFE4E1EC, true);
    }

    /** The recap under the death screen's buttons. */
    private static void drawRecap(GuiGraphicsExtractor graphics, List<String> lines) {
        Font font = Minecraft.getInstance().font;
        boolean spanish = CrystalUi.spanish();
        String title = spanish ? "Resumen de cristales" : "Crystal recap";
        int width = font.width(title);
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        width = Math.min(graphics.guiWidth() - 16, width + 16);
        int height = 16 + lines.size() * 10;
        int x = (graphics.guiWidth() - width) / 2;
        int y = Math.max(4, graphics.guiHeight() - height - 8);
        HubDraw.glass(graphics, x, y, width, height, 0xD8160A22, 0xD80C0612, 0xFFA31234, 1.0F);
        graphics.text(font, title, x + 8, y + 4, 0xFFFF7A9A, true);
        int lineY = y + 15;
        for (String line : lines) {
            graphics.text(font, HubDraw.fit(font, line, width - 16), x + 8, lineY, 0xFFE4E1EC, true);
            lineY += 10;
        }
    }
}
