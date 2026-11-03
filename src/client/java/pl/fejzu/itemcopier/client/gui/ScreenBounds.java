package pl.fejzu.itemcopier.client.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import pl.fejzu.itemcopier.client.mixin.AbstractContainerScreenAccessor;

public record ScreenBounds(int left, int top, int width, int height) {

    private static final int CREATIVE_TAB_ROW = 28;

    public static ScreenBounds of(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;

        int left = accessor.getLeftPos();
        int top = accessor.getTopPos();
        int right = left + accessor.getImageWidth();
        int bottom = top + accessor.getImageHeight();

        if (screen instanceof CreativeModeInventoryScreen) {
            top -= CREATIVE_TAB_ROW;
            bottom += CREATIVE_TAB_ROW;
        }

        return new ScreenBounds(left, top, right - left, bottom - top);
    }

    public ScreenBounds scaled(float scale) {
        if (scale == 1.0f) {
            return this;
        }
        return new ScreenBounds(
                (int) (left / scale),
                (int) (top / scale),
                (int) Math.ceil(width / scale),
                (int) Math.ceil(height / scale));
    }
}
