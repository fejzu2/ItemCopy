package pl.fejzu.itemcopier.client.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.function.UnaryOperator;

public final class ColorCodes {
    private ColorCodes() {
    }

    public static MutableComponent parse(String raw) {
        return parse(raw, UnaryOperator.identity());
    }

    public static MutableComponent parse(String raw, UnaryOperator<Style> extraStyle) {
        MutableComponent result = Component.literal("");
        Style baseStyle = extraStyle.apply(Style.EMPTY);
        Style style = baseStyle;
        StringBuilder current = new StringBuilder();

        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);

            if (c == '&' && i + 7 < raw.length() && raw.charAt(i + 1) == '#' && isHex6(raw, i + 2)) {
                if (current.length() > 0) {
                    result.append(Component.literal(current.toString()).setStyle(style));
                    current.setLength(0);
                }
                int rgb = Integer.parseInt(raw.substring(i + 2, i + 8), 16);
                style = style.withColor(rgb);
                i += 8;
                continue;
            }

            if (c == '&' && i + 1 < raw.length() && ChatFormatting.getByCode(raw.charAt(i + 1)) != null) {
                if (current.length() > 0) {
                    result.append(Component.literal(current.toString()).setStyle(style));
                    current.setLength(0);
                }
                ChatFormatting formatting = ChatFormatting.getByCode(raw.charAt(i + 1));
                style = (formatting == ChatFormatting.RESET) ? baseStyle : style.applyFormat(formatting);
                i += 2;
                continue;
            }

            current.append(c);
            i++;
        }

        if (current.length() > 0) {
            result.append(Component.literal(current.toString()).setStyle(style));
        }
        return result;
    }

    private static boolean isHex6(String raw, int start) {
        for (int i = start; i < start + 6; i++) {
            if (Character.digit(raw.charAt(i), 16) == -1) {
                return false;
            }
        }
        return true;
    }
}
