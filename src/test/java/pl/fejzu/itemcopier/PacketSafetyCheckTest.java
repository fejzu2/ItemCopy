package pl.fejzu.itemcopier;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.fejzu.itemcopier.client.serialization.PacketSafetyCheck;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketSafetyCheckTest {
    private static RegistryAccess registries;
    private static FeatureFlagSet enabled;

    @BeforeAll
    static void boot() {
        registries = TestRegistries.registryAccess();
        enabled = FeatureFlags.DEFAULT_FLAGS;
    }

    @Test
    @DisplayName("a normal item is accepted and reports its encoded size")
    void normalItemIsSafe() {
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Napiersnik THANATOSA"));

        PacketSafetyCheck.Result result = PacketSafetyCheck.validate(stack, 9, registries, enabled);

        assertTrue(result.safe(), String.valueOf(result.reason()));
        assertNull(result.reason());
        assertTrue(result.encodedBytes() > 0, "expected a positive encoded size");
    }

    @Test
    @DisplayName("an empty stack is refused")
    void emptyStackRefused() {
        PacketSafetyCheck.Result result =
                PacketSafetyCheck.validate(ItemStack.EMPTY, 9, registries, enabled);
        assertFalse(result.safe());
        assertNotNull(result.reason());
    }

    @Test
    @DisplayName("a slot the server would reject is refused before anything is sent")
    void slotRangeIsEnforced() {
        ItemStack stack = new ItemStack(Items.STONE);

        assertFalse(PacketSafetyCheck.validate(stack, 0, registries, enabled).safe(),
                "slot 0 is outside the range vanilla accepts");
        assertFalse(PacketSafetyCheck.validate(stack, 46, registries, enabled).safe(),
                "slot 46 is outside the range vanilla accepts");
        assertFalse(PacketSafetyCheck.validate(stack, -1, registries, enabled).safe());

        assertTrue(PacketSafetyCheck.validate(stack, PacketSafetyCheck.MIN_SLOT,
                registries, enabled).safe());
        assertTrue(PacketSafetyCheck.validate(stack, PacketSafetyCheck.MAX_SLOT,
                registries, enabled).safe());
    }

    @Test
    @DisplayName("an over-sized stack is refused, because the server drops it silently")
    void overSizedStackRefused() {
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.setCount(64);

        PacketSafetyCheck.Result result = PacketSafetyCheck.validate(stack, 9, registries, enabled);

        assertFalse(result.safe());
        assertNotNull(result.reason());
        assertTrue(result.reason().contains("max stack size"), result.reason());
    }

    @Test
    @DisplayName("13. an item with a multi-megabyte payload is refused instead of breaking the connection")
    void oversizedPayloadRefused() {
        CompoundTag payload = new CompoundTag();
        String blob = "x".repeat(20_000);
        for (int i = 0; i < 100; i++) {
            payload.putString("key_" + i, blob);
        }

        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(payload));

        PacketSafetyCheck.Result result = PacketSafetyCheck.validate(stack, 9, registries, enabled);

        assertFalse(result.safe(), "a multi-megabyte item must not be sent");
        assertNotNull(result.reason());
    }

    @Test
    @DisplayName("a large but legal payload is still accepted")
    void largeButLegalPayloadAccepted() {
        List<Component> lore = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            lore.add(Component.literal("lore line " + i + " " + "y".repeat(200)));
        }
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.LORE, new ItemLore(lore));

        PacketSafetyCheck.Result result = PacketSafetyCheck.validate(stack, 9, registries, enabled);

        assertTrue(result.safe(), String.valueOf(result.reason()));
        assertTrue(result.encodedBytes() > 10_000,
                "expected a sizeable payload, got " + result.encodedBytes());
    }

    @Test
    @DisplayName("the check never throws, whatever it is handed")
    void neverThrows() {
        assertFalse(PacketSafetyCheck.validate(null, 9, registries, enabled).safe());
        ItemStack stack = new ItemStack(Items.STONE);
        assertTrue(PacketSafetyCheck.validate(stack, 9, registries, null).safe());
    }
}
