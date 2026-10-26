package com.zymekoh.crystaltweaks.client.practice;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Before 26.2 Minecraft's world renderer cannot be given a second world to draw, so the window in the
 * top-left corner shows the parked server without a live view of its world. See the 26.2 copy.
 */
final class ServerView {
    private ServerView() {
    }

    static String unavailableReason(boolean spanish) {
        return spanish
                ? "La vista en vivo necesita Minecraft 26.2 o posterior."
                : "The live view needs Minecraft 26.2 or later.";
    }

    static Identifier texture() {
        return null;
    }

    static float aspect() {
        return 16.0F / 9.0F;
    }

    static void render(Minecraft minecraft) {
    }

    static void tick() {
    }

    static void close() {
    }
}
