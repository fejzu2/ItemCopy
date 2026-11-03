package pl.fejzu.itemcopier.client.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class Messages {
    private static final String RESOURCE_PATH = "/assets/itemcopier/messages/messages.json";
    private static final Logger LOGGER = LoggerFactory.getLogger("ItemCopier");
    private static final Map<String, List<String>> MESSAGES = load();

    private Messages() {
    }

    private static Map<String, List<String>> load() {
        try (InputStream in = Messages.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.warn("Could not find {} - messages will fall back to their keys.", RESOURCE_PATH);
                return Map.of();
            }
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> parsed =
                    new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
            return parsed != null ? parsed : Map.of();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to load {}", RESOURCE_PATH, e);
            return Map.of();
        }
    }

    private static String raw(String key) {
        List<String> lines = MESSAGES.get(key);
        if (lines == null || lines.isEmpty()) {
            return "&c[ItemCopier] Missing message for key: " + key;
        }
        return String.join("\n", lines);
    }

    private static String substitute(String template, Map<String, String> placeholders) {
        String result = template.replace("%prefix%", raw("prefix"));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    public static MutableComponent text(String key) {
        return text(key, Map.of());
    }

    public static MutableComponent text(String key, Map<String, String> placeholders) {
        return ColorCodes.parse(substitute(raw(key), placeholders));
    }

    public static MutableComponent text(String key, Map<String, String> placeholders, UnaryOperator<Style> extraStyle) {
        return ColorCodes.parse(substitute(raw(key), placeholders), extraStyle);
    }

    public static Component prefix() {
        return ColorCodes.parse(raw("prefix"));
    }

    public static void send(String key) {
        send(key, Map.of());
    }

    public static void send(String key, Map<String, String> placeholders) {
        send(text(key, placeholders));
    }

    public static void send(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(message);
        } else {
            LOGGER.info("{}", message.getString());
        }
    }
}
