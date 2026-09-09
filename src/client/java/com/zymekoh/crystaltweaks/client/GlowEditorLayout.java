package com.zymekoh.crystaltweaks.client;

/** Pure layout calculation, also exercised without starting Minecraft. */
public record GlowEditorLayout(int previewX, int previewWidth, int previewHeight,
        int optionsX, int optionsY, int optionsWidth, int optionsHeight) {
    public static GlowEditorLayout fit(int x, int y, int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        // The options column below needs four rows plus a usable colour picker. Half the height for
        // the preview left the picker permanently below the fold, so it takes a smaller share.
        int previewHeight = height >= 75 ? Math.min(64, height * 2 / 5) : 0;
        int previewWidth = previewHeight > 0 ? Math.min(width, 150) : 0;
        int reserved = previewHeight > 0 ? previewHeight + 5 : 0;
        int optionsWidth = Math.min(310, width);
        return new GlowEditorLayout(x + (width - previewWidth) / 2, previewWidth, previewHeight,
                x + (width - optionsWidth) / 2, y + reserved, optionsWidth, Math.max(1, height - reserved));
    }
}
