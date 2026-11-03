package pl.fejzu.itemcopier.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import org.lwjgl.glfw.GLFW;

public final class SearchField {
    private static final int MAX_LENGTH = 64;
    private static final long CURSOR_BLINK_MS = 500L;

    private String value = "";
    private int cursor;
    private boolean focused;
    private long focusedAt;

    private int x;
    private int y;
    private int width;
    private int height;

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public String value() {
        return value;
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        if (this.focused != focused) {
            this.focused = focused;
            this.focusedAt = System.currentTimeMillis();
        }
    }

    public void clear() {
        value = "";
        cursor = 0;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean containsClearButton(double mouseX, double mouseY) {
        if (value.isEmpty()) {
            return false;
        }
        int buttonX = x + width - height;
        return mouseX >= buttonX && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean charTyped(int codepoint) {
        if (!focused || !StringUtil.isAllowedChatCharacter(codepoint)) {
            return false;
        }
        if (value.length() >= MAX_LENGTH) {
            return true;
        }
        String typed = new String(Character.toChars(codepoint));
        value = value.substring(0, cursor) + typed + value.substring(cursor);
        cursor += typed.length();
        return true;
    }

    public boolean keyPressed(int key) {
        if (!focused) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
                    value = value.substring(0, cursor - 1) + value.substring(cursor);
                    cursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < value.length()) {
                    value = value.substring(0, cursor) + value.substring(cursor + 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = Math.max(0, cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = Math.min(value.length(), cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = value.length();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (!value.isEmpty()) {
                    clear();
                } else {
                    setFocused(false);
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                setFocused(false);
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    public void render(GuiGraphicsExtractor gfx, Font font, int mouseX, int mouseY) {
        gfx.fill(x, y, x + width, y + height, GuiStyle.FIELD_BG);
        GuiStyle.border(gfx, x, y, width, height,
                focused ? GuiStyle.FIELD_BORDER_FOCUSED : GuiStyle.FIELD_BORDER);

        int textX = x + 3;
        int textY = y + (height - font.lineHeight) / 2 + 1;
        int textRoom = width - 6 - (value.isEmpty() ? 0 : height);

        if (value.isEmpty()) {
            gfx.text(font, Component.translatable("itemcopier.panel.search"),
                    textX, textY, GuiStyle.TEXT_DIM, false);
        } else {
            String shown = font.plainSubstrByWidth(value, textRoom);
            gfx.text(font, shown, textX, textY, GuiStyle.TEXT, false);
        }

        if (focused && cursorVisible()) {
            String beforeCursor = value.substring(0, Math.min(cursor, value.length()));
            int cursorX = textX + Math.min(font.width(beforeCursor), textRoom);
            gfx.fill(cursorX, y + 2, cursorX + 1, y + height - 2, GuiStyle.ACCENT_GOLD);
        }

        if (!value.isEmpty()) {
            boolean hovered = containsClearButton(mouseX, mouseY);
            int buttonX = x + width - height;
            gfx.text(font, "×", buttonX + (height - font.width("×")) / 2, textY,
                    hovered ? GuiStyle.ACCENT_RED : GuiStyle.TEXT_MUTED, false);
        }
    }

    private boolean cursorVisible() {
        return ((System.currentTimeMillis() - focusedAt) / CURSOR_BLINK_MS) % 2 == 0;
    }
}
