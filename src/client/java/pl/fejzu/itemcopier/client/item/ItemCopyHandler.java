package pl.fejzu.itemcopier.client.item;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;
import pl.fejzu.itemcopier.client.mixin.AbstractContainerScreenAccessor;
import pl.fejzu.itemcopier.client.serialization.ItemStackSerializer;
import pl.fejzu.itemcopier.client.storage.ItemLibrary;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;
import pl.fejzu.itemcopier.client.util.Messages;

import java.util.Map;

public final class ItemCopyHandler {
    private final ItemCopierConfig config;
    private final ItemLibrary library;

    private String lastCopiedId;
    private long lastCopiedAt;

    public ItemCopyHandler(ItemCopierConfig config, ItemLibrary library) {
        this.config = config;
        this.library = library;
    }

    public String lastCopiedId() {
        return lastCopiedId;
    }

    public long lastCopiedAt() {
        return lastCopiedAt;
    }

    public boolean copyHovered(AbstractContainerScreen<?> screen) {
        return copyHovered(screen, false);
    }

    public boolean copyHovered(AbstractContainerScreen<?> screen, boolean dropLore) {
        Slot hovered = ((AbstractContainerScreenAccessor) screen).getHoveredSlot();
        if (hovered == null) {
            Messages.send("copy-no-slot");
            return true;
        }

        ItemStack stack = hovered.getItem();
        if (stack == null || stack.isEmpty()) {
            Messages.send("copy-empty-slot");
            return true;
        }
        return copy(stack, dropLore);
    }

    public boolean copy(ItemStack stack) {
        return copy(stack, false);
    }

    public boolean copy(ItemStack stack, boolean dropLore) {
        Minecraft client = Minecraft.getInstance();
        RegistryAccess registries = registries(client);
        if (registries == null) {
            Messages.send("copy-failed", Map.of("error", "no registry context - are you in a world?"));
            return true;
        }

        ItemStack prepared = stack.copy();
        int strippedLines = 0;
        if (dropLore) {
            prepared = LoreEditor.withoutLore(prepared);
        } else {
            int before = LoreEditor.linesOf(prepared).size();
            prepared = LoreEditor.dropLastLines(prepared, config.stripLastLoreLines);
            prepared = LoreEditor.applyPatterns(prepared, config.lorePatterns());
            strippedLines = before - LoreEditor.linesOf(prepared).size();
        }

        CopiedItem item;
        try {
            item = CopiedItem.capture(
                    prepared,
                    registries,
                    config.recordSourceServer ? currentServer(client) : null,
                    SharedConstants.getCurrentVersion().name(),
                    SharedConstants.WORLD_VERSION
            );
        } catch (ItemStackSerializer.SerializationException e) {
            ItemCopierLog.warn("Could not serialize the hovered item: {}", e.getMessage());
            Messages.send("copy-failed", Map.of("error", String.valueOf(e.getMessage())));
            return true;
        } catch (RuntimeException e) {
            ItemCopierLog.error("Unexpected failure while copying an item", e);
            Messages.send("copy-failed", Map.of("error", e.getClass().getSimpleName()));
            return true;
        }

        ItemLibrary.AddOutcome outcome = library.add(item);
        library.flush();

        String name = item.displayName().getString();
        switch (outcome) {
            case ADDED -> {
                lastCopiedId = item.id();
                lastCopiedAt = System.currentTimeMillis();
                ItemCopierLog.debug("Copied {} ({} components)",
                        item.itemId(), prepared.getComponents().size());
                if (config.copyNotifications) {
                    if (dropLore) {
                        Messages.send("copied-no-lore", Map.of("item", name));
                    } else if (strippedLines > 0) {
                        Messages.send("copied-lore-stripped", Map.of(
                                "item", name,
                                "count", String.valueOf(strippedLines)));
                    } else {
                        Messages.send("copied", Map.of("item", name));
                    }
                }
            }
            case DUPLICATE_SKIPPED -> {
                if (config.copyNotifications) {
                    Messages.send("copied-duplicate", Map.of("item", name));
                }
            }
            case DUPLICATE_REFRESHED -> {
                if (config.copyNotifications) {
                    Messages.send("copied-replaced", Map.of("item", name));
                }
            }
            case LIBRARY_FULL -> Messages.send("library-full",
                    Map.of("max", String.valueOf(config.maxLibrarySize)));
        }
        return true;
    }

    public static RegistryAccess registries(Minecraft client) {
        if (client.getConnection() != null) {
            return client.getConnection().registryAccess();
        }
        if (client.level != null) {
            return client.level.registryAccess();
        }
        return null;
    }

    private static String currentServer(Minecraft client) {
        ServerData data = client.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) {
            return data.ip;
        }
        if (client.hasSingleplayerServer()) {
            return "singleplayer";
        }
        return null;
    }
}
