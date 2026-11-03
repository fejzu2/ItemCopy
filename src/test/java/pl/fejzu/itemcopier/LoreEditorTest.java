package pl.fejzu.itemcopier;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.fejzu.itemcopier.client.item.LoreEditor;
import pl.fejzu.itemcopier.client.serialization.ItemStackSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreEditorTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void boot() {
        registries = TestRegistries.lookup();
    }

    private ItemStack shopItem() {
        ItemStack stack = new ItemStack(Items.LEATHER_BOOTS);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("BUTY TANATOSA *8"));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x332244));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Niezniszczalnosc 16"),
                Component.literal("Ochrona 16"),
                Component.literal("---"),
                Component.literal("Cena: $128 lub *128"),
                Component.literal("Przedmiot mozesz ulepszyc w poprzednim menu")
        )));
        return stack;
    }

    @Test
    @DisplayName("the lines of an item are listed in order")
    void listsLines() {
        assertEquals(5, LoreEditor.linesOf(shopItem()).size());
        assertEquals("Ochrona 16", LoreEditor.linesOf(shopItem()).get(1).getString());
        assertTrue(LoreEditor.linesOf(new ItemStack(Items.STONE)).isEmpty());
        assertTrue(LoreEditor.linesOf(ItemStack.EMPTY).isEmpty());
    }

    @Test
    @DisplayName("keeping a subset drops only the unwanted lines")
    void keepsSubset() {
        ItemStack original = shopItem();
        List<Component> all = LoreEditor.linesOf(original);
        List<Component> kept = List.of(all.get(0), all.get(1));

        ItemStack edited = LoreEditor.withLore(original, kept);
        List<Component> result = LoreEditor.linesOf(edited);

        assertEquals(2, result.size());
        assertEquals("Niezniszczalnosc 16", result.get(0).getString());
        assertEquals("Ochrona 16", result.get(1).getString());
        assertEquals(5, LoreEditor.linesOf(original).size(), "the original must not be touched");
    }

    @Test
    @DisplayName("every other component survives a lore edit")
    void keepsOtherComponents() {
        ItemStack original = shopItem();
        ItemStack edited = LoreEditor.withLore(original, LoreEditor.linesOf(original).subList(0, 2));

        assertEquals("BUTY TANATOSA *8", edited.get(DataComponents.CUSTOM_NAME).getString());
        assertNotNull(edited.get(DataComponents.DYED_COLOR));
        assertEquals(0x332244, edited.get(DataComponents.DYED_COLOR).rgb());
        assertEquals(Items.LEATHER_BOOTS, edited.getItem());
    }

    @Test
    @DisplayName("dropping every line removes the lore component instead of leaving it empty")
    void emptyLoreRemovesComponent() {
        ItemStack edited = LoreEditor.withoutLore(shopItem());
        assertNull(edited.get(DataComponents.LORE), "an empty lore component should not be stored");
        assertNotNull(edited.get(DataComponents.CUSTOM_NAME));
    }

    @Test
    @DisplayName("an edited item re-encodes and reads back with the shorter lore")
    void reencodeRoundTrip() throws Exception {
        ItemStack original = shopItem();
        List<Component> kept = LoreEditor.linesOf(original).subList(0, 2);

        CompoundTag tag = LoreEditor.reencodeWithLore(original, kept, registries);
        ItemStack restored = ItemStackSerializer.decode(tag, registries);

        assertEquals(2, LoreEditor.linesOf(restored).size());
        assertEquals("BUTY TANATOSA *8", restored.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(0x332244, restored.get(DataComponents.DYED_COLOR).rgb());
    }

    @Test
    @DisplayName("patterns drop the shop lines and leave the stats alone")
    void patternsDropShopLines() {
        ItemStack stripped = LoreEditor.applyPatterns(shopItem(), List.of("Cena:", "ulepszyc"));
        List<Component> result = LoreEditor.linesOf(stripped);

        assertEquals(3, result.size());
        assertEquals("Niezniszczalnosc 16", result.get(0).getString());
        assertEquals("Ochrona 16", result.get(1).getString());
        assertEquals("---", result.get(2).getString());
    }

    @Test
    @DisplayName("pattern matching ignores case and surrounding text")
    void patternMatching() {
        assertTrue(LoreEditor.matchesAny("Cena: $128 lub *128", List.of("cena:")));
        assertTrue(LoreEditor.matchesAny("Cena: $128", List.of("  CENA:  ")));
        assertFalse(LoreEditor.matchesAny("Ochrona 16", List.of("cena:")));
        assertFalse(LoreEditor.matchesAny("Ochrona 16", List.of("", "   ")));
        assertFalse(LoreEditor.matchesAny("Ochrona 16", List.of()));
    }

    @Test
    @DisplayName("an item with no matching lines comes back unchanged")
    void noMatchLeavesStackAlone() {
        ItemStack original = shopItem();
        ItemStack result = LoreEditor.applyPatterns(original, List.of("nothing-matches-this"));
        assertEquals(5, LoreEditor.linesOf(result).size());
    }

    @Test
    @DisplayName("a suggested pattern cuts the line off before the part that varies")
    void suggestsPatternPrefix() {
        assertEquals("Cena:", LoreEditor.suggestPattern("Cena: $128 lub *128"));
        assertEquals("Cena:", LoreEditor.suggestPattern("  Cena: 999  "));
        assertEquals("Poziom", LoreEditor.suggestPattern("Poziom 42"));
        assertEquals("Wymagany level:", LoreEditor.suggestPattern("Wymagany level: 30"));
    }

    @Test
    @DisplayName("a line with nothing variable is kept whole as the pattern")
    void suggestsWholeLineWhenNothingVaries() {
        assertEquals("Przedmiot mozesz ulepszyc w poprzednim menu",
                LoreEditor.suggestPattern("Przedmiot mozesz ulepszyc w poprzednim menu"));
    }

    @Test
    @DisplayName("a suggestion that would be too short falls back to the whole line")
    void shortPrefixFallsBackToWholeLine() {
        assertEquals("x 128", LoreEditor.suggestPattern("x 128"));
        assertEquals("$128", LoreEditor.suggestPattern("$128"));
        assertEquals("", LoreEditor.suggestPattern("   "));
        assertEquals("", LoreEditor.suggestPattern(null));
    }

    @Test
    @DisplayName("a suggested pattern actually matches the line it came from")
    void suggestionMatchesItsOwnLine() {
        String line = "Cena: $128 lub *128";
        String pattern = LoreEditor.suggestPattern(line);
        assertTrue(LoreEditor.matchesAny(line, List.of(pattern)));
        assertTrue(LoreEditor.matchesAny("Cena: $9999 lub *1", List.of(pattern)),
                "the pattern should also catch the same line with a different price");
        assertFalse(LoreEditor.matchesAny("Ochrona 16", List.of(pattern)));
    }

    @Test
    @DisplayName("the shop lines from the KrabMC tooltip are stripped whatever the price is")
    void krabmcShopLines() {
        List<String> patterns = List.of("Cena:", "ulepszy");

        assertTrue(LoreEditor.matchesAny("✕ Cena: $128 lub ★128", patterns));
        assertTrue(LoreEditor.matchesAny("✕ Cena: $9 lub ★9", patterns));
        assertTrue(LoreEditor.matchesAny("✕ Cena: $999999 lub ★999999", patterns));
        assertTrue(LoreEditor.matchesAny("Przedmiot możesz ulepszyć w poprzednim menu", patterns));

        assertFalse(LoreEditor.matchesAny("Niezniszczalność 16", patterns));
        assertFalse(LoreEditor.matchesAny("Ochrona 16", patterns));
        assertFalse(LoreEditor.matchesAny("BUTY TANATOSA ★8", patterns));
    }

    @Test
    @DisplayName("copying that tooltip with those patterns keeps only the stats")
    void krabmcItemKeepsOnlyStats() {
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponents.CUSTOM_NAME, Component.literal("BUTY TANATOSA ★8"));
        boots.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("[♥] Niezniszczalność 16"),
                Component.literal("[♥] Ochrona 16"),
                Component.literal("✕ Cena: $128 lub ★128"),
                Component.literal("Przedmiot możesz ulepszyć w poprzednim menu")
        )));

        ItemStack cleaned = LoreEditor.applyPatterns(boots, List.of("Cena:", "ulepszy"));
        List<Component> lore = LoreEditor.linesOf(cleaned);

        assertEquals(2, lore.size());
        assertTrue(lore.get(0).getString().contains("Niezniszczalno"));
        assertTrue(lore.get(1).getString().contains("Ochrona"));
        assertEquals("BUTY TANATOSA ★8", cleaned.get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test
    @DisplayName("blank lines left behind by a pattern are cleaned up too")
    void patternRemovalCleansUpBlanks() {
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(""),
                Component.literal("[❤] Ochrona 16"),
                Component.literal("[Ὦ1] Niezniszczalnosc 16"),
                Component.literal("⎯‹⎯«⎯»⎯›⎯"),
                Component.literal(""),
                Component.literal("✕ Cena: $128 lub ★128"),
                Component.literal("Przedmiot mozesz ulepszyc w poprzednim menu"),
                Component.literal("")
        )));

        ItemStack cleaned = LoreEditor.applyPatterns(boots, List.of("Cena:", "ulepszy"));
        List<Component> lore = LoreEditor.linesOf(cleaned);

        assertEquals(4, lore.size(),
                "the blank spacers around the removed lines should not survive: " + lore);
        assertEquals("", lore.get(0).getString());
        assertTrue(lore.get(1).getString().contains("Ochrona"));
        assertTrue(lore.get(2).getString().contains("Niezniszczalnosc"));
        assertTrue(lore.get(3).getString().startsWith("⎯"));
    }

    @Test
    @DisplayName("trimming drops trailing blanks and collapses runs of them")
    void trimBlankDebris() {
        List<Component> trimmed = LoreEditor.trimBlankDebris(List.of(
                Component.literal(""),
                Component.literal("a"),
                Component.literal(""),
                Component.literal(""),
                Component.literal("b"),
                Component.literal(""),
                Component.literal("")
        ));

        assertEquals(4, trimmed.size());
        assertEquals("", trimmed.get(0).getString());
        assertEquals("a", trimmed.get(1).getString());
        assertEquals("", trimmed.get(2).getString());
        assertEquals("b", trimmed.get(3).getString());
    }

    @Test
    @DisplayName("an item whose lore is untouched keeps its blank lines exactly")
    void noRemovalKeepsBlanksUntouched() {
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(""),
                Component.literal("Ochrona 16"),
                Component.literal(""),
                Component.literal("")
        )));

        ItemStack result = LoreEditor.applyPatterns(boots, List.of("nic-nie-pasuje"));
        assertEquals(4, LoreEditor.linesOf(result).size(),
                "nothing matched, so nothing - including blanks - may change");
    }

    @Test
    @DisplayName("blank detection treats whitespace-only lines as blank")
    void blankDetection() {
        assertTrue(LoreEditor.isBlank(Component.literal("")));
        assertTrue(LoreEditor.isBlank(Component.literal("   ")));
        assertTrue(LoreEditor.isBlank(null));
        assertFalse(LoreEditor.isBlank(Component.literal("Ochrona 16")));
    }

    private static final String SHOP_SEPARATOR =
            "⎯‹" + "⎯".repeat(8) + "«" + "⎯".repeat(10)
                    + "»" + "⎯".repeat(8) + "›⎯";

    private static Component gradient(String text) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            int shade = 0x17DD62 + i * 0x010101;
            line.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(shade))));
        }
        return line;
    }

    private static List<String> textOf(ItemStack stack) {
        return LoreEditor.linesOf(stack).stream().map(Component::getString).toList();
    }

    @Test
    @DisplayName("patterns match a gradient line split into one component per character")
    void gradientLinesAreMatched() {
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponents.LORE, new ItemLore(List.of(
                gradient(""),
                gradient("Ochrona 16"),
                gradient("Cena: 12500$"),
                gradient(SHOP_SEPARATOR),
                gradient(""),
                gradient("")
        )));

        ItemStack result = LoreEditor.applyPatterns(boots, List.of("Cena:", "ulepszy", SHOP_SEPARATOR));

        assertEquals(List.of("", "Ochrona 16"), textOf(result));
    }

    @Test
    @DisplayName("one bracket character strips a separator of any width, a full copy does not")
    void shortPatternSurvivesWidthChanges() {
        String narrower = "⎯‹⎯«⎯»⎯›⎯";
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponents.LORE, new ItemLore(List.of(
                gradient("Ochrona 16"),
                gradient(narrower)
        )));

        assertEquals(List.of("Ochrona 16"),
                textOf(LoreEditor.applyPatterns(boots, List.of("«"))));
        assertEquals(List.of("Ochrona 16", narrower),
                textOf(LoreEditor.applyPatterns(boots, List.of(SHOP_SEPARATOR))));
    }

    @Test
    @DisplayName("dropping the last lines removes exactly that many from the bottom")
    void dropsExactlyTheLastLines() {
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Ochrona 16"),
                Component.literal("Niezniszczalnosc 16"),
                Component.literal("Cena: 12500$"),
                Component.literal("Kliknij aby kupic"),
                Component.literal("Zostalo: 3 sztuki")
        )));

        ItemStack result = LoreEditor.dropLastLines(boots, 3);

        assertEquals(List.of("Ochrona 16", "Niezniszczalnosc 16"), textOf(result));
    }

    @Test
    @DisplayName("dropping more lines than there are leaves no lore component at all")
    void dropLastBeyondTheLore() {
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Ochrona 16"),
                Component.literal("Cena: 12500$")
        )));

        ItemStack result = LoreEditor.dropLastLines(boots, 9);

        assertNull(result.get(DataComponents.LORE));
        assertEquals(List.of(), textOf(result));
    }

    @Test
    @DisplayName("zero or less keeps the lore as it was, blank lines included")
    void dropLastZeroChangesNothing() {
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(""),
                Component.literal("Ochrona 16"),
                Component.literal(""),
                Component.literal("")
        )));

        assertEquals(List.of("", "Ochrona 16", "", ""), textOf(LoreEditor.dropLastLines(boots, 0)));
        assertEquals(List.of("", "Ochrona 16", "", ""), textOf(LoreEditor.dropLastLines(boots, -4)));
    }

    @Test
    @DisplayName("blanks exposed by the cut are tidied up, not left hanging")
    void dropLastTrimsBlanksLeftBehind() {
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Ochrona 16"),
                Component.literal(""),
                Component.literal(""),
                Component.literal("Cena: 12500$")
        )));

        assertEquals(List.of("Ochrona 16"), textOf(LoreEditor.dropLastLines(boots, 1)));
    }

    @Test
    @DisplayName("the copy pipeline cuts the tail first, then applies the patterns")
    void tailCutAndPatternsCompose() {
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponents.LORE, new ItemLore(List.of(
                gradient("Ochrona 16"),
                gradient("Cena: 12500$"),
                gradient(SHOP_SEPARATOR),
                gradient("Kliknij aby kupic"),
                gradient("Zostalo: 3 sztuki")
        )));

        ItemStack result = LoreEditor.applyPatterns(
                LoreEditor.dropLastLines(boots, 2), List.of("Cena:", "«"));

        assertEquals(List.of("Ochrona 16"), textOf(result));
    }
}
