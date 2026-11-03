package pl.fejzu.itemcopier.client.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import pl.fejzu.itemcopier.client.serialization.ItemStackSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LoreEditor {

    private LoreEditor() {
    }

    public static List<Component> linesOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore == null ? List.of() : List.copyOf(lore.lines());
    }

    public static ItemStack withLore(ItemStack stack, List<Component> keptLines) {
        ItemStack copy = stack.copy();
        if (keptLines.isEmpty()) {
            copy.remove(DataComponents.LORE);
        } else {
            copy.set(DataComponents.LORE, new ItemLore(List.copyOf(keptLines)));
        }
        return copy;
    }

    public static CompoundTag reencodeWithLore(
            ItemStack stack,
            List<Component> keptLines,
            HolderLookup.Provider registries
    ) throws ItemStackSerializer.SerializationException {
        return ItemStackSerializer.encode(withLore(stack, keptLines), registries);
    }

    public static ItemStack applyPatterns(ItemStack stack, List<String> patterns) {
        if (stack == null || stack.isEmpty() || patterns == null || patterns.isEmpty()) {
            return stack;
        }
        List<Component> lines = linesOf(stack);
        if (lines.isEmpty()) {
            return stack;
        }

        List<Component> kept = new ArrayList<>(lines.size());
        for (Component line : lines) {
            if (!matchesAny(line.getString(), patterns)) {
                kept.add(line);
            }
        }
        if (kept.size() == lines.size()) {
            return stack;
        }
        return withLore(stack, trimBlankDebris(kept));
    }

    public static ItemStack dropLastLines(ItemStack stack, int count) {
        if (stack == null || stack.isEmpty() || count <= 0) {
            return stack;
        }
        List<Component> lines = linesOf(stack);
        if (lines.isEmpty()) {
            return stack;
        }
        int keep = Math.max(0, lines.size() - count);
        return withLore(stack, trimBlankDebris(List.copyOf(lines.subList(0, keep))));
    }

    public static boolean isBlank(Component line) {
        return line == null || line.getString().isBlank();
    }

    public static List<Component> trimBlankDebris(List<Component> lines) {
        List<Component> collapsed = new ArrayList<>(lines.size());
        for (Component line : lines) {
            boolean blank = isBlank(line);
            boolean previousBlank = !collapsed.isEmpty() && isBlank(collapsed.get(collapsed.size() - 1));
            if (blank && previousBlank) {
                continue;
            }
            collapsed.add(line);
        }
        while (!collapsed.isEmpty() && isBlank(collapsed.get(collapsed.size() - 1))) {
            collapsed.remove(collapsed.size() - 1);
        }
        return collapsed;
    }

    public static boolean matchesAny(String text, List<String> patterns) {
        String haystack = text.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (haystack.contains(pattern.toLowerCase(Locale.ROOT).trim())) {
                return true;
            }
        }
        return false;
    }

    private static final String VARIABLE_MARKERS = "$€★*§";

    public static String suggestPattern(String line) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty()) {
            return "";
        }
        int cut = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c) || VARIABLE_MARKERS.indexOf(c) >= 0) {
                cut = i;
                break;
            }
        }
        if (cut < 0) {
            return text;
        }
        String prefix = text.substring(0, cut).trim();
        while (!prefix.isEmpty() && "-_.,;|".indexOf(prefix.charAt(prefix.length() - 1)) >= 0) {
            prefix = prefix.substring(0, prefix.length() - 1).trim();
        }
        return prefix.length() >= 3 ? prefix : text;
    }

    public static ItemStack withoutLore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }
        return withLore(stack, List.of());
    }
}
