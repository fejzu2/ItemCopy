package pl.fejzu.itemcopier.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class GuiStyle {
    public static final int PANEL_TOP = 0xF21A1A22;
    public static final int PANEL_BOTTOM = 0xF20E0E13;
    public static final int BORDER = 0xFF2E2E3C;
    public static final int BORDER_INNER = 0x33FFFFFF;
    public static final int DIVIDER = 0xFF262632;

    public static final int SLOT_BG = 0x40000000;
    public static final int SLOT_BORDER = 0xFF2A2A36;
    public static final int SLOT_HOVER = 0x33FFD700;
    public static final int SLOT_HOVER_BORDER = 0xFFFFD700;
    public static final int SLOT_SELECTED_BORDER = 0xFF17DD62;
    public static final int SLOT_BROKEN = 0x33FF4D4D;
    public static final int SLOT_BROKEN_BORDER = 0xFFFF4D4D;

    public static final int TEXT = 0xFFE6E6F0;
    public static final int TEXT_MUTED = 0xFF8C8C9E;
    public static final int TEXT_DIM = 0xFF5E5E70;
    public static final int ACCENT_GOLD = 0xFFFFD700;
    public static final int ACCENT_GREEN = 0xFF17DD62;
    public static final int ACCENT_RED = 0xFFFF5555;

    public static final int FIELD_BG = 0xFF0A0A0E;
    public static final int FIELD_BORDER = 0xFF32323F;
    public static final int FIELD_BORDER_FOCUSED = 0xFFFFD700;
    public static final int BUTTON_BG = 0xFF1E1E28;
    public static final int BUTTON_BG_HOVER = 0xFF2C2C3A;
    public static final int BUTTON_BORDER = 0xFF3A3A4A;
    public static final int SCROLLBAR_TRACK = 0xFF15151C;
    public static final int SCROLLBAR_TRACK_SOFT = 0x40000000;
    public static final int SCROLLBAR_THUMB = 0xFF46465A;
    public static final int SCROLLBAR_THUMB_HOVER = 0xFFFFD700;

    public static final int SLOT_SIZE = 18;
    public static final int PADDING = 5;
    public static final int HEADER_HEIGHT = 13;
    public static final int FOOTER_HEIGHT = 11;
    public static final int SEARCH_HEIGHT = 12;
    public static final int SCROLLBAR_WIDTH = 4;
    public static final int GAP = 3;

    private GuiStyle() {
    }

    public static void panel(GuiGraphicsExtractor gfx, int x, int y, int width, int height) {
        gfx.fillGradient(x, y, x + width, y + height, PANEL_TOP, PANEL_BOTTOM);
        border(gfx, x, y, width, height, BORDER);
        gfx.fill(x + 1, y + 1, x + width - 1, y + 2, BORDER_INNER);
    }

    public static void border(GuiGraphicsExtractor gfx, int x, int y, int width, int height, int color) {
        gfx.fill(x, y, x + width, y + 1, color);
        gfx.fill(x, y + height - 1, x + width, y + height, color);
        gfx.fill(x, y + 1, x + 1, y + height - 1, color);
        gfx.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void divider(GuiGraphicsExtractor gfx, int x, int y, int width) {
        gfx.fill(x, y, x + width, y + 1, DIVIDER);
    }

    public static void slot(GuiGraphicsExtractor gfx, int x, int y) {
        gfx.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_BG);
        border(gfx, x, y, SLOT_SIZE, SLOT_SIZE, SLOT_BORDER);
    }

    public static void slotHighlight(GuiGraphicsExtractor gfx, int x, int y, int fill, int borderColor) {
        gfx.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, fill);
        border(gfx, x, y, SLOT_SIZE, SLOT_SIZE, borderColor);
    }

    public static final int GEAR_SIZE = 8;

    public static void gear(GuiGraphicsExtractor gfx, int x, int y, int color) {
        gfx.fill(x + 2, y + 2, x + 6, y + 3, color);
        gfx.fill(x + 2, y + 5, x + 6, y + 6, color);
        gfx.fill(x + 2, y + 2, x + 3, y + 6, color);
        gfx.fill(x + 5, y + 2, x + 6, y + 6, color);

        gfx.fill(x + 3, y, x + 5, y + 2, color);
        gfx.fill(x + 3, y + 6, x + 5, y + 8, color);
        gfx.fill(x, y + 3, x + 2, y + 5, color);
        gfx.fill(x + 6, y + 3, x + 8, y + 5, color);
    }

    public static float ease(float t) {
        float clamped = Math.clamp(t, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    public static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.clamp(alpha, 0.0f, 1.0f));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
