package pl.fejzu.itemcopier.client.serialization;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public final class GiveCommandBuilder {
    public static final int CHAT_INPUT_LIMIT = 256;

    private GiveCommandBuilder() {
    }

    public record Result(String command, List<String> skipped, int components) {
        public int length() {
            return command.length();
        }

        public boolean fitsInChat() {
            return command.length() <= CHAT_INPUT_LIMIT;
        }

        public boolean isComplete() {
            return skipped.isEmpty() && fitsInChat();
        }
    }

    public static Result build(CompoundTag stackTag, String targetSelector) {
        String itemId = stackTag.getStringOr("id", "minecraft:air");
        int count = stackTag.getIntOr("count", 1);
        CompoundTag components = stackTag.getCompoundOrEmpty("components");

        List<String> arguments = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (String key : components.keySet()) {
            Tag value = components.get(key);
            if (value == null) {
                skipped.add(key);
                continue;
            }
            String snbt;
            try {
                snbt = ItemStackSerializer.toSnbt(value);
            } catch (RuntimeException e) {
                skipped.add(key);
                continue;
            }
            if (snbt == null || snbt.isEmpty()) {
                skipped.add(key);
                continue;
            }
            arguments.add(key + "=" + snbt);
        }

        StringBuilder command = new StringBuilder("/give ");
        command.append(targetSelector).append(' ').append(itemId);
        if (!arguments.isEmpty()) {
            command.append('[').append(String.join(",", arguments)).append(']');
        }
        if (count != 1) {
            command.append(' ').append(count);
        }
        return new Result(command.toString(), List.copyOf(skipped), arguments.size());
    }
}
