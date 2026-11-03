package pl.fejzu.itemcopier;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.fejzu.itemcopier.client.item.CopiedItem;
import pl.fejzu.itemcopier.client.storage.LibraryCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryStorageTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void boot() {
        registries = TestRegistries.lookup();
    }

    private CopiedItem capture(ItemStack stack, String server) throws Exception {
        return CopiedItem.capture(stack, registries, server, "26.2", 4900);
    }

    private static List<CopiedItem> writeAndRead(Path file, List<CopiedItem> items) throws IOException {
        NbtIo.writeCompressed(LibraryCodec.write(items), file);
        CompoundTag read = NbtIo.readCompressed(file, NbtAccounter.create(64L * 1024 * 1024));
        return LibraryCodec.read(read).items();
    }

    @Test
    @DisplayName("7. a library survives a save and a reload with every field intact")
    void saveAndReload(@TempDir Path dir) throws Exception {
        ItemStack chestplate = new ItemStack(Items.LEATHER_CHESTPLATE);
        chestplate.set(DataComponents.CUSTOM_NAME, Component.literal("Napiersnik THANATOSA")
                .withStyle(style -> style.withColor(0xFF5555)));
        chestplate.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("tier 8"))));
        chestplate.set(DataComponents.DYED_COLOR, new DyedItemColor(0x2277AA));

        CopiedItem first = capture(chestplate, "play.example.com");
        first.setLabel("my favourite");
        CopiedItem second = capture(new ItemStack(Items.DIAMOND_SWORD, 3), "other.example.com");

        List<CopiedItem> reloaded = writeAndRead(dir.resolve("library.nbt"), List.of(first, second));

        assertEquals(2, reloaded.size());

        CopiedItem reloadedFirst = reloaded.getFirst();
        assertEquals(first.id(), reloadedFirst.id());
        assertEquals("my favourite", reloadedFirst.label());
        assertEquals("play.example.com", reloadedFirst.sourceServer());
        assertEquals("minecraft:leather_chestplate", reloadedFirst.itemId());
        assertEquals("26.2", reloadedFirst.minecraftVersion());
        assertEquals(4900, reloadedFirst.dataVersion());
        assertEquals(first.copiedAt().toEpochMilli(), reloadedFirst.copiedAt().toEpochMilli());

        ItemStack restoredStack = reloadedFirst.stack(registries);
        assertFalse(restoredStack.isEmpty());
        assertFalse(reloadedFirst.isBroken());
        assertTrue(ItemStack.isSameItemSameComponents(chestplate, restoredStack),
                "components did not survive the save/reload");

        CopiedItem reloadedSecond = reloaded.get(1);
        assertEquals(3, reloadedSecond.count());
        assertNull(reloadedSecond.label());
        assertEquals(3, reloadedSecond.stack(registries).getCount());
    }

    @Test
    @DisplayName("9. a damaged file loses only the damaged records")
    void malformedEntriesAreSkippedNotFatal(@TempDir Path dir) throws Exception {
        CopiedItem good = capture(new ItemStack(Items.STONE, 7), null);

        CompoundTag root = LibraryCodec.write(List.of(good));
        ListTag entries = root.getListOrEmpty("entries");

        entries.add(StringTag.valueOf("this is not an entry"));
        CompoundTag noStack = new CompoundTag();
        noStack.putString("id", "abc");
        entries.add(noStack);
        CompoundTag unknownItem = new CompoundTag();
        unknownItem.putString("id", "ghost");
        CompoundTag unknownStack = new CompoundTag();
        unknownStack.putString("id", "itemcopier:no_such_item");
        unknownStack.putInt("count", 1);
        unknownItem.put("stack", unknownStack);
        entries.add(unknownItem);

        Path file = dir.resolve("library.nbt");
        NbtIo.writeCompressed(root, file);
        CompoundTag readBack = NbtIo.readCompressed(file, NbtAccounter.create(1024 * 1024));
        LibraryCodec.LoadResult result = LibraryCodec.read(readBack);

        assertEquals(2, result.skipped(), "wrong number of skipped records");
        assertEquals(2, result.items().size());

        CopiedItem loadedGood = result.items().getFirst();
        assertEquals(7, loadedGood.stack(registries).getCount());
        assertFalse(loadedGood.isBroken());

        CopiedItem loadedGhost = result.items().get(1);
        assertTrue(loadedGhost.stack(registries).isEmpty());
        assertTrue(loadedGhost.isBroken(), "an undecodable item should report itself broken");
        assertNotNull(loadedGhost.decodeError());
        assertEquals("itemcopier:no_such_item", loadedGhost.itemId());
    }

    @Test
    @DisplayName("an entry that cannot be decoded is still written back unchanged")
    void brokenEntryIsPreservedOnResave(@TempDir Path dir) throws Exception {
        CompoundTag unknownStack = new CompoundTag();
        unknownStack.putString("id", "itemcopier:no_such_item");
        unknownStack.putInt("count", 5);
        CompoundTag components = new CompoundTag();
        components.putString("itemcopier:unknown_component", "payload we must not lose");
        unknownStack.put("components", components);

        CompoundTag entry = new CompoundTag();
        entry.putString("id", "keep-me");
        entry.put("stack", unknownStack);
        entry.putLong("copied_at", 1_700_000_000_000L);

        CompoundTag root = new CompoundTag();
        root.putInt("format", LibraryCodec.FORMAT_VERSION);
        ListTag entries = new ListTag();
        entries.add(entry);
        root.put("entries", entries);

        CopiedItem loaded = LibraryCodec.read(root).items().getFirst();
        assertTrue(loaded.stack(registries).isEmpty());
        assertTrue(loaded.isBroken());

        List<CopiedItem> reloaded = writeAndRead(dir.resolve("library.nbt"), List.of(loaded));
        assertEquals(1, reloaded.size());
        assertEquals(unknownStack, reloaded.getFirst().stackTag(),
                "the raw payload of an unreadable entry was altered on re-save");
        assertEquals("keep-me", reloaded.getFirst().id());
    }

    @Test
    @DisplayName("an empty library writes and reads back as empty")
    void emptyLibrary(@TempDir Path dir) throws Exception {
        List<CopiedItem> reloaded = writeAndRead(dir.resolve("library.nbt"), List.of());
        assertTrue(reloaded.isEmpty());
        assertTrue(Files.size(dir.resolve("library.nbt")) > 0);
    }

    @Test
    @DisplayName("a large library round-trips without losing entries")
    void largeLibrary(@TempDir Path dir) throws Exception {
        List<CopiedItem> many = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) {
            ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("item " + i));
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(i * 37));
            many.add(capture(stack, "server-" + (i % 4)));
        }

        List<CopiedItem> reloaded = writeAndRead(dir.resolve("library.nbt"), many);
        assertEquals(500, reloaded.size());
        assertEquals("item 499", reloaded.getLast().stack(registries)
                .get(DataComponents.CUSTOM_NAME).getString());
    }
}
