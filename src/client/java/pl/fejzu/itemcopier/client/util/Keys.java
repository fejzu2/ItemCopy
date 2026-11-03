package pl.fejzu.itemcopier.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Keys {
    private Keys() {
    }

    public static boolean isShiftDown() {
        return isDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean isControlDown() {
        return isDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean isDown(int key) {
        var window = Minecraft.getInstance().getWindow();
        return window != null && InputConstants.isKeyDown(window, key);
    }
}
