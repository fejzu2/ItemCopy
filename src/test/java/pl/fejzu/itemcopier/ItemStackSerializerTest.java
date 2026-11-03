package pl.fejzu.itemcopier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.fejzu.itemcopier.client.serialization.ItemStackSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemStackSerializerTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void boot() {
        registries = TestRegistries.lookup();
    }

    private ItemStack roundTrip(ItemStack original) throws Exception {
        CompoundTag tag = ItemStackSerializer.encode(original, registries);
        assertNotNull(tag, "encode produced no tag");
        return ItemStackSerializer.decode(tag, registries);
    }

    @Test
    @DisplayName("1. an ordinary item keeps its item and count")
    void plainItem() throws Exception {
        ItemStack original = new ItemStack(Items.STONE, 42);
        ItemStack restored = roundTrip(original);

        assertEquals(Items.STONE, restored.getItem());
        assertEquals(42, restored.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(original, restored));
    }

    @Test
    @DisplayName("2. enchantments survive, with their levels")
    void enchantedItem() throws Exception {
        CompoundTag enchantments = new CompoundTag();
        enchantments.putInt(Enchantments.SHARPNESS.identifier().toString(), 5);
        enchantments.putInt(Enchantments.UNBREAKING.identifier().toString(), 3);

        CompoundTag components = new CompoundTag();
        components.put("minecraft:enchantments", enchantments);

        CompoundTag fromServer = new CompoundTag();
        fromServer.putString("id", "minecraft:diamond_sword");
        fromServer.putInt("count", 1);
        fromServer.put("components", components);

        ItemStack decoded = ItemStackSerializer.decode(fromServer, registries);
        assertEquals(Items.DIAMOND_SWORD, decoded.getItem());

        ItemEnchantments decodedEnchantments = decoded.get(DataComponents.ENCHANTMENTS);
        assertNotNull(decodedEnchantments, "enchantments component was not decoded");
        assertEquals(2, decodedEnchantments.size());

        ItemStack restored = roundTrip(decoded);
        ItemEnchantments restoredEnchantments = restored.get(DataComponents.ENCHANTMENTS);

        assertNotNull(restoredEnchantments);
        assertEquals(2, restoredEnchantments.size());
        assertTrue(ItemStack.isSameItemSameComponents(decoded, restored),
                "the enchanted stack changed across the round trip");

        ResourceKey<Enchantment> sharpness = Enchantments.SHARPNESS;
        int sharpnessLevel = restoredEnchantments.entrySet().stream()
                .filter(e -> e.getKey().is(sharpness))
                .mapToInt(it.unimi.dsi.fastutil.objects.Object2IntMap.Entry::getIntValue)
                .findFirst()
                .orElse(-1);
        assertEquals(5, sharpnessLevel);
    }

    @Test
    @DisplayName("3. a coloured custom name and multi-line lore survive")
    void customNameAndLore() throws Exception {
        Component name = Component.literal("Napiersnik THANATOSA *8")
                .withStyle(style -> style.withColor(0xFF5555).withBold(true));
        ItemLore lore = new ItemLore(List.of(
                Component.literal("Line one").withStyle(ChatFormatting.GRAY),
                Component.literal("Line two").withStyle(ChatFormatting.DARK_PURPLE)
        ));

        ItemStack original = new ItemStack(Items.LEATHER_CHESTPLATE);
        original.set(DataComponents.CUSTOM_NAME, name);
        original.set(DataComponents.LORE, lore);

        ItemStack restored = roundTrip(original);

        Component restoredName = restored.get(DataComponents.CUSTOM_NAME);
        assertNotNull(restoredName);
        assertEquals("Napiersnik THANATOSA *8", restoredName.getString());
        assertNotNull(restoredName.getStyle().getColor());
        assertEquals(0xFF5555, restoredName.getStyle().getColor().getValue());
        assertTrue(restoredName.getStyle().isBold());

        ItemLore restoredLore = restored.get(DataComponents.LORE);
        assertNotNull(restoredLore);
        assertEquals(2, restoredLore.lines().size());
        assertEquals("Line one", restoredLore.lines().get(0).getString());
        assertEquals("Line two", restoredLore.lines().get(1).getString());
    }

    @Test
    @DisplayName("4. CustomModelData survives in all four of its lists")
    void customModelData() throws Exception {
        CustomModelData data = new CustomModelData(
                List.of(1.5f, 2.5f),
                List.of(true, false),
                List.of("thanatos", "tier8"),
                List.of(0xAABBCC)
        );
        ItemStack original = new ItemStack(Items.LEATHER_CHESTPLATE);
        original.set(DataComponents.CUSTOM_MODEL_DATA, data);

        ItemStack restored = roundTrip(original);
        CustomModelData restoredData = restored.get(DataComponents.CUSTOM_MODEL_DATA);

        assertNotNull(restoredData);
        assertEquals(List.of(1.5f, 2.5f), restoredData.floats());
        assertEquals(List.of(true, false), restoredData.flags());
        assertEquals(List.of("thanatos", "tier8"), restoredData.strings());
        assertEquals(List.of(0xAABBCC), restoredData.colors());
    }

    @Test
    @DisplayName("5. a dyed leather armour colour survives exactly")
    void dyedLeatherArmor() throws Exception {
        ItemStack original = new ItemStack(Items.LEATHER_CHESTPLATE);
        original.set(DataComponents.DYED_COLOR, new DyedItemColor(0x1A9F3C));

        ItemStack restored = roundTrip(original);
        DyedItemColor color = restored.get(DataComponents.DYED_COLOR);

        assertNotNull(color);
        assertEquals(0x1A9F3C, color.rgb());
    }

    @Test
    @DisplayName("an item with many components keeps every one of them")
    void allComponentsTogether() throws Exception {
        ItemStack original = new ItemStack(Items.LEATHER_CHESTPLATE, 1);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Everything"));
        original.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("lore"))));
        original.set(DataComponents.DYED_COLOR, new DyedItemColor(0x112233));
        original.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of("x"), List.of()));
        original.set(DataComponents.MAX_STACK_SIZE, 1);

        ItemStack restored = roundTrip(original);

        assertEquals(original.getComponents().size(), restored.getComponents().size(),
                "component count changed across the round trip");
        assertTrue(ItemStack.isSameItemSameComponents(original, restored),
                "components differ after the round trip");
    }

    @Test
    @DisplayName("the stored tag is a snapshot - editing the original does not change it")
    void encodeIsASnapshot() throws Exception {
        ItemStack original = new ItemStack(Items.STONE, 1);
        CompoundTag tag = ItemStackSerializer.encode(original.copy(), registries);

        original.setCount(64);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("changed later"));

        ItemStack restored = ItemStackSerializer.decode(tag, registries);
        assertEquals(1, restored.getCount());
        assertFalse(restored.has(DataComponents.CUSTOM_NAME));
    }

    @Test
    @DisplayName("an empty stack is refused rather than stored as air")
    void emptyStackRefused() {
        assertThrows(ItemStackSerializer.SerializationException.class,
                () -> ItemStackSerializer.encode(ItemStack.EMPTY, registries));
    }

    @Test
    @DisplayName("garbage item data reports an error instead of throwing something unexpected")
    void brokenTagReportsError() {
        CompoundTag broken = new CompoundTag();
        broken.putString("id", "itemcopier:does_not_exist");
        broken.putInt("count", 1);

        ItemStackSerializer.SerializationException thrown =
                assertThrows(ItemStackSerializer.SerializationException.class,
                        () -> ItemStackSerializer.decode(broken, registries));
        assertNotNull(thrown.getMessage());
    }

    @Test
    @DisplayName("SNBT output is produced for the clipboard buttons")
    void snbtOutput() throws Exception {
        ItemStack original = new ItemStack(Items.LEATHER_CHESTPLATE);
        original.set(DataComponents.DYED_COLOR, new DyedItemColor(0x1A9F3C));
        CompoundTag tag = ItemStackSerializer.encode(original, registries);

        String compact = ItemStackSerializer.toSnbt(tag);
        String pretty = ItemStackSerializer.toPrettySnbt(tag);

        assertTrue(compact.contains("leather_chestplate"), compact);
        assertTrue(compact.contains("dyed_color"), compact);
        assertFalse(compact.contains("\n"), "compact SNBT must stay on one line");
        assertTrue(pretty.contains("dyed_color"), pretty);
    }
}
