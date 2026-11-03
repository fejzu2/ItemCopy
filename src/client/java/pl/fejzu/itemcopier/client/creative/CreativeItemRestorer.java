package pl.fejzu.itemcopier.client.creative;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import pl.fejzu.itemcopier.client.item.CopiedItem;
import pl.fejzu.itemcopier.client.serialization.PacketSafetyCheck;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;

public final class CreativeItemRestorer {
    private CreativeItemRestorer() {
    }

    public enum Availability {
        ALLOWED,
        NO_PLAYER,
        NOT_CREATIVE
    }

    public record Result(boolean success, String messageKey, String detail) {
        public static Result ok() {
            return new Result(true, "restore-ok", null);
        }

        public static Result failure(String messageKey, String detail) {
            return new Result(false, messageKey, detail);
        }
    }

    public static Availability availability() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || client.getConnection() == null) {
            return Availability.NO_PLAYER;
        }
        if (!player.hasInfiniteMaterials()) {
            return Availability.NOT_CREATIVE;
        }
        return Availability.ALLOWED;
    }

    public static boolean canRestore() {
        return availability() == Availability.ALLOWED;
    }

    public static Result insert(CopiedItem item) {
        int slot = findFreeInventorySlot();
        if (slot < 0) {
            return Result.failure("restore-no-space", null);
        }
        return insertIntoMenuSlot(item, slot);
    }

    public static Result insertIntoSlot(CopiedItem item, Slot target) {
        int menuSlot = toInventoryMenuSlot(target);
        if (menuSlot < 0) {
            return null;
        }
        return insertIntoMenuSlot(item, menuSlot);
    }

    private static Result insertIntoMenuSlot(CopiedItem item, int menuSlot) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientPacketListener connection = client.getConnection();

        Availability availability = availability();
        if (availability != Availability.ALLOWED) {
            return Result.failure("restore-no-permission", availability.name());
        }

        RegistryAccess registries = connection.registryAccess();
        ItemStack stack = item.stack(registries);
        if (stack.isEmpty()) {
            return Result.failure("restore-rejected",
                    item.decodeError() != null ? item.decodeError() : "item could not be read");
        }

        ItemStack toSend = stack.copy();

        PacketSafetyCheck.Result check =
                PacketSafetyCheck.validate(toSend, menuSlot, registries, connection.enabledFeatures());
        if (!check.safe()) {
            ItemCopierLog.warn("Refusing to send item {} ({}): {}",
                    item.itemId(), item.plainDisplayName(), check.reason());
            if (check.reason() != null && check.reason().contains("feature flags")) {
                return Result.failure("restore-disabled-item", item.itemId());
            }
            return Result.failure("restore-rejected", check.reason());
        }

        ItemCopierLog.debug("Sending creative slot {} with {} ({} bytes encoded)",
                menuSlot, item.itemId(), check.encodedBytes());

        client.gameMode.handleCreativeModeItemAdd(toSend, menuSlot);

        player.inventoryMenu.getSlot(menuSlot).set(toSend.copy());
        return Result.ok();
    }

    public static int findFreeInventorySlot() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return -1;
        }
        InventoryMenu menu = player.inventoryMenu;
        for (int slot = InventoryMenu.INV_SLOT_START; slot < InventoryMenu.INV_SLOT_END; slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) {
                return slot;
            }
        }
        for (int slot = InventoryMenu.USE_ROW_SLOT_START; slot < InventoryMenu.USE_ROW_SLOT_END; slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public static int toInventoryMenuSlot(Slot target) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || target == null) {
            return -1;
        }
        Inventory inventory = player.getInventory();
        if (target.container != inventory) {
            return -1;
        }
        int containerSlot = target.getContainerSlot();
        InventoryMenu menu = player.inventoryMenu;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot candidate = menu.slots.get(i);
            if (candidate.container == inventory && candidate.getContainerSlot() == containerSlot) {
                int index = candidate.index;
                if (index >= PacketSafetyCheck.MIN_SLOT && index <= PacketSafetyCheck.MAX_SLOT) {
                    return index;
                }
                return -1;
            }
        }
        return -1;
    }
}
