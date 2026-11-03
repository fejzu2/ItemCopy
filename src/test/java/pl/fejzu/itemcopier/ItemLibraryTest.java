package pl.fejzu.itemcopier;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.fejzu.itemcopier.client.config.DuplicateMode;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;
import pl.fejzu.itemcopier.client.config.SortOrder;
import pl.fejzu.itemcopier.client.item.CopiedItem;
import pl.fejzu.itemcopier.client.storage.ItemLibrary;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemLibraryTest {
    private static HolderLookup.Provider registries;

    private ItemCopierConfig config;
    private ItemLibrary library;

    @BeforeAll
    static void boot() {
        registries = TestRegistries.lookup();
    }

    @BeforeEach
    void setUp() {
        config = new ItemCopierConfig();
        library = new ItemLibrary(config);
    }

    private CopiedItem named(String name, String server) throws Exception {
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return CopiedItem.capture(stack, registries, server, "26.2", 4900);
    }

    @Test
    @DisplayName("skip keeps the first copy and leaves it untouched")
    void duplicateSkip() throws Exception {
        config.setDuplicates(DuplicateMode.SKIP);
        CopiedItem first = named("Thanatos", "a.example.com");
        assertEquals(ItemLibrary.AddOutcome.ADDED, library.add(first));

        CopiedItem again = named("Thanatos", "a.example.com");
        assertEquals(ItemLibrary.AddOutcome.DUPLICATE_SKIPPED, library.add(again));

        assertEquals(1, library.size());
        assertSame(first, library.all().getFirst());
    }

    @Test
    @DisplayName("allow stores the second copy as its own entry")
    void duplicateAllow() throws Exception {
        config.setDuplicates(DuplicateMode.ALLOW);
        library.add(named("Thanatos", "a.example.com"));
        assertEquals(ItemLibrary.AddOutcome.ADDED, library.add(named("Thanatos", "a.example.com")));

        assertEquals(2, library.size());
        assertNotEquals(library.all().get(0).id(), library.all().get(1).id());
    }

    @Test
    @DisplayName("refresh replaces the entry in place and keeps the user label")
    void duplicateRefreshKeepsLabel() throws Exception {
        config.setDuplicates(DuplicateMode.REFRESH);
        CopiedItem first = named("Thanatos", "a.example.com");
        library.add(first);
        library.rename(first, "my chestplate");

        Thread.sleep(5);
        assertEquals(ItemLibrary.AddOutcome.DUPLICATE_REFRESHED,
                library.add(named("Thanatos", "a.example.com")));

        assertEquals(1, library.size());
        CopiedItem refreshed = library.all().getFirst();
        assertEquals("my chestplate", refreshed.label(), "the rename was lost on refresh");
        assertTrue(refreshed.copiedAt().isAfter(first.copiedAt()),
                "refresh should move the entry to the top with a newer timestamp");
    }

    @Test
    @DisplayName("the size cap refuses new entries instead of dropping old ones")
    void sizeCapDoesNotDeleteAnything() throws Exception {
        config.maxLibrarySize = 2;
        config.setDuplicates(DuplicateMode.ALLOW);
        library.add(named("one", null));
        library.add(named("two", null));

        assertEquals(ItemLibrary.AddOutcome.LIBRARY_FULL, library.add(named("three", null)));
        assertEquals(2, library.size());
        assertEquals("one", library.all().get(0).plainDisplayName());
        assertEquals("two", library.all().get(1).plainDisplayName());
    }

    @Test
    @DisplayName("search matches the name, the registry id and the source server")
    void search() throws Exception {
        config.setDuplicates(DuplicateMode.ALLOW);
        library.add(named("Napiersnik THANATOSA", "shop.example.com"));
        library.add(named("Plain Helmet", "other.example.com"));
        CopiedItem sword = CopiedItem.capture(
                new ItemStack(Items.DIAMOND_SWORD), registries, "other.example.com", "26.2", 4900);
        library.add(sword);

        assertEquals(1, library.view("thanatos", SortOrder.NEWEST).size(), "name search");
        assertEquals(1, library.view("THANATOSA", SortOrder.NEWEST).size(), "search is case-insensitive");
        assertEquals(1, library.view("diamond_sword", SortOrder.NEWEST).size(), "registry id search");
        assertEquals(2, library.view("other.example.com", SortOrder.NEWEST).size(), "server search");
        assertEquals(3, library.view("", SortOrder.NEWEST).size(), "empty query matches everything");
        assertEquals(0, library.view("nothing-here", SortOrder.NEWEST).size());
    }

    @Test
    @DisplayName("a user label is what search and the grid use")
    void labelIsSearchable() throws Exception {
        CopiedItem item = named("Napiersnik THANATOSA", null);
        library.add(item);
        library.rename(item, "boss drop");

        assertEquals("boss drop", item.plainDisplayName());
        assertEquals(1, library.view("boss", SortOrder.NEWEST).size());
        assertEquals(1, library.view("thanatos", SortOrder.NEWEST).size());
    }

    @Test
    @DisplayName("sorting by newest, oldest and name all order correctly")
    void sorting() throws Exception {
        config.setDuplicates(DuplicateMode.ALLOW);
        CopiedItem charlie = named("charlie", null);
        library.add(charlie);
        Thread.sleep(5);
        CopiedItem alpha = named("alpha", null);
        library.add(alpha);
        Thread.sleep(5);
        CopiedItem bravo = named("bravo", null);
        library.add(bravo);

        List<CopiedItem> newest = library.view("", SortOrder.NEWEST);
        assertEquals(List.of("bravo", "alpha", "charlie"),
                newest.stream().map(CopiedItem::plainDisplayName).toList());

        List<CopiedItem> oldest = library.view("", SortOrder.OLDEST);
        assertEquals(List.of("charlie", "alpha", "bravo"),
                oldest.stream().map(CopiedItem::plainDisplayName).toList());

        List<CopiedItem> byName = library.view("", SortOrder.NAME);
        assertEquals(List.of("alpha", "bravo", "charlie"),
                byName.stream().map(CopiedItem::plainDisplayName).toList());
    }

    @Test
    @DisplayName("removing and clearing report what they removed")
    void removeAndClear() throws Exception {
        config.setDuplicates(DuplicateMode.ALLOW);
        CopiedItem one = named("one", null);
        library.add(one);
        library.add(named("two", null));

        assertTrue(library.remove(one));
        assertEquals(1, library.size());
        assertTrue(library.byId(one.id()).isEmpty());

        assertEquals(1, library.clear());
        assertTrue(library.isEmpty());
    }
}
