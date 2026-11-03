package pl.fejzu.itemcopier.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;
import pl.fejzu.itemcopier.client.input.CopyKeybinding;
import pl.fejzu.itemcopier.client.input.ScreenHooks;
import pl.fejzu.itemcopier.client.item.ItemCopyHandler;
import pl.fejzu.itemcopier.client.storage.ItemLibrary;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;

public class ItemCopierClient implements ClientModInitializer {
    private static ItemCopierConfig config;
    private static ItemLibrary library;

    @Override
    public void onInitializeClient() {
        config = ItemCopierConfig.load();
        library = new ItemLibrary(config);
        library.loadFromDisk();

        ItemCopyHandler copyHandler = new ItemCopyHandler(config, library);
        CopyKeybinding.register();
        ScreenHooks hooks = new ScreenHooks(config, library, copyHandler);
        hooks.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (CopyKeybinding.consumeLibraryClick() && client.gui.screen() == null) {
                hooks.openLibrary();
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                library.resolveAll(handler.registryAccess()));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> library.flush());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> library.flush());

        ItemCopierLog.info("ItemCopier ready - {} saved item(s)", library.size());
    }

    public static ItemCopierConfig config() {
        return config;
    }

    public static ItemLibrary library() {
        return library;
    }
}
