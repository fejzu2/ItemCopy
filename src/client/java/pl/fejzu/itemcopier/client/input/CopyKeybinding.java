package pl.fejzu.itemcopier.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import pl.fejzu.itemcopier.ItemCopier;

public final class CopyKeybinding {
    private static KeyMapping copyKey;
    private static KeyMapping libraryKey;

    private CopyKeybinding() {
    }

    public static void register() {
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ItemCopier.MOD_ID, "main"));

        copyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.itemcopier.copy",
                GLFW.GLFW_KEY_A,
                category
        ));
        libraryKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.itemcopier.library",
                GLFW.GLFW_KEY_B,
                category
        ));
    }

    public static boolean isCopyKey(KeyEvent event) {
        return copyKey != null && !copyKey.isUnbound() && copyKey.matches(event);
    }

    public static boolean isLibraryKey(KeyEvent event) {
        return libraryKey != null && !libraryKey.isUnbound() && libraryKey.matches(event);
    }

    public static boolean consumeLibraryClick() {
        if (libraryKey == null || libraryKey.isUnbound()) {
            return false;
        }
        boolean pressed = false;
        while (libraryKey.consumeClick()) {
            pressed = true;
        }
        return pressed;
    }

    public static String boundKeyName() {
        if (copyKey == null) {
            return "A";
        }
        if (copyKey.isUnbound()) {
            return "-";
        }
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(copyKey);
        return key.getDisplayName().getString();
    }
}
