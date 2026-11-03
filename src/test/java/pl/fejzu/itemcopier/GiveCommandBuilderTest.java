package pl.fejzu.itemcopier;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.fejzu.itemcopier.client.serialization.GiveCommandBuilder;
import pl.fejzu.itemcopier.client.serialization.ItemStackSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GiveCommandBuilderTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void boot() {
        registries = TestRegistries.lookup();
    }

    @Test
    @DisplayName("a command carries the item id, every component and the count")
    void buildsFullCommand() throws Exception {
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE, 2);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Thanatos"));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x1A9F3C));

        CompoundTag tag = ItemStackSerializer.encode(stack, registries);
        GiveCommandBuilder.Result result = GiveCommandBuilder.build(tag, "@s");

        assertTrue(result.skipped().isEmpty(), "unexpectedly skipped: " + result.skipped());
        assertEquals(2, result.components());
        assertTrue(result.command().startsWith("/give @s minecraft:leather_chestplate["),
                result.command());
        assertTrue(result.command().contains("minecraft:custom_name="), result.command());
        assertTrue(result.command().contains("minecraft:dyed_color="), result.command());
        assertTrue(result.command().endsWith(" 2"), result.command());
        assertTrue(result.isComplete(), "a short command should be reported as complete");
    }

    @Test
    @DisplayName("a single item leaves the count off, the way the command expects")
    void singleItemHasNoCount() throws Exception {
        CompoundTag tag = ItemStackSerializer.encode(new ItemStack(Items.STONE), registries);
        GiveCommandBuilder.Result result = GiveCommandBuilder.build(tag, "@s");

        assertEquals("/give @s minecraft:stone", result.command());
        assertEquals(0, result.components());
        assertTrue(result.isComplete());
    }

    @Test
    @DisplayName("an item with no components produces no bracket section")
    void noComponentsNoBrackets() throws Exception {
        CompoundTag tag = ItemStackSerializer.encode(new ItemStack(Items.STONE, 5), registries);
        GiveCommandBuilder.Result result = GiveCommandBuilder.build(tag, "Steve");

        assertEquals("/give Steve minecraft:stone 5", result.command());
    }

    @Test
    @DisplayName("a command too long for the chat input is reported as incomplete")
    void longCommandIsFlagged() throws Exception {
        List<Component> lore = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            lore.add(Component.literal("Lore line number " + i + " with some length to it"));
        }
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Napiersnik THANATOSA"));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x1A9F3C));

        CompoundTag tag = ItemStackSerializer.encode(stack, registries);
        GiveCommandBuilder.Result result = GiveCommandBuilder.build(tag, "@s");

        assertTrue(result.length() > GiveCommandBuilder.CHAT_INPUT_LIMIT,
                "expected a long command, got " + result.length() + " characters");
        assertFalse(result.fitsInChat());
        assertFalse(result.isComplete(),
                "a command that would be truncated must not be reported as complete");
        assertTrue(result.skipped().isEmpty());
        assertTrue(result.command().contains("minecraft:lore="), result.command());
    }

    @Test
    @DisplayName("the length reported is the length of the command handed to the clipboard")
    void lengthMatchesCommand() throws Exception {
        CompoundTag tag = ItemStackSerializer.encode(new ItemStack(Items.STONE, 3), registries);
        GiveCommandBuilder.Result result = GiveCommandBuilder.build(tag, "@s");
        assertEquals(result.command().length(), result.length());
    }
}
