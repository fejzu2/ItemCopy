package pl.fejzu.itemcopier.client.input;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;
import pl.fejzu.itemcopier.client.gui.ItemCopierPanel;
import pl.fejzu.itemcopier.client.gui.ItemCopierScreen;
import pl.fejzu.itemcopier.client.item.ItemCopyHandler;
import pl.fejzu.itemcopier.client.storage.ItemLibrary;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;
import pl.fejzu.itemcopier.client.util.Keys;

public final class ScreenHooks {
    private final ItemCopierConfig config;
    private final ItemLibrary library;
    private final ItemCopyHandler copyHandler;

    public ScreenHooks(ItemCopierConfig config, ItemLibrary library, ItemCopyHandler copyHandler) {
        this.config = config;
        this.library = library;
        this.copyHandler = copyHandler;
    }

    public void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) {
                return;
            }
            ItemCopierPanel panel = new ItemCopierPanel(config, library, copyHandler);
            ItemCopierLog.debug("Attached panel to {}", screen.getClass().getSimpleName());

            ScreenEvents.afterExtract(screen).register((s, gfx, mouseX, mouseY, tickDelta) -> {
                try {
                    panel.render(container, gfx, mouseX, mouseY, tickDelta);
                } catch (RuntimeException e) {
                    ItemCopierLog.error("Panel render failed - hiding it on this screen", e);
                    panel.disableAfterFailure();
                }
            });

            ScreenEvents.remove(screen).register(s -> {
                panel.reset();
                library.flush();
            });

            ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> {
                if (panel.keyPressed(keyEvent.key())) {
                    return false;
                }
                if (client.options.keyDrop.matches(keyEvent) && panel.deleteHovered()) {
                    return false;
                }
                if (CopyKeybinding.isCopyKey(keyEvent)) {
                    return !copyHandler.copyHovered(container, Keys.isControlDown());
                }
                if (CopyKeybinding.isLibraryKey(keyEvent)) {
                    client.setScreenAndShow(new ItemCopierScreen(screen, config, library));
                    return false;
                }
                return true;
            });

            ScreenKeyboardEvents.allowCharType(screen).register((s, charEvent) ->
                    !panel.charTyped(charEvent.codepoint()));

            ScreenMouseEvents.allowMouseClick(screen).register((s, mouseEvent) ->
                    !panel.mouseClicked(container, mouseEvent.x(), mouseEvent.y(), mouseEvent.button()));

            ScreenMouseEvents.allowMouseRelease(screen).register((s, mouseEvent) ->
                    !panel.mouseReleased(container, mouseEvent.x(), mouseEvent.y(), mouseEvent.button()));

            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) ->
                    !panel.mouseScrolled(mouseX, mouseY, vertical));
        });
    }

    public void openLibrary() {
        Minecraft client = Minecraft.getInstance();
        client.setScreenAndShow(new ItemCopierScreen(client.gui.screen(), config, library));
    }
}
