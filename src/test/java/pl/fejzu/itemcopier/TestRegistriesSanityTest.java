package pl.fejzu.itemcopier;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRegistriesSanityTest {
    @Test
    @DisplayName("the test registries are real serialization owners for their own holders")
    void registriesOwnTheirHolders() {
        HolderLookup.Provider provider = TestRegistries.lookup();
        HolderLookup.RegistryLookup<Enchantment> enchantments =
                provider.lookupOrThrow(Registries.ENCHANTMENT);

        assertTrue(enchantments.getOrThrow(Enchantments.SHARPNESS).canSerializeIn(enchantments),
                "sharpness cannot be serialized against the registry that produced it");

        long total = enchantments.listElements().count();
        long serializable = enchantments.listElements()
                .filter(holder -> holder.canSerializeIn(enchantments))
                .count();
        assertTrue(total > 0, "no enchantments were loaded - the vanilla data pack is missing");
        assertEquals(total, serializable,
                "some enchantment holders are not owned by their own registry lookup");
    }

    @Test
    @DisplayName("repeated lookups return the same registry instance")
    void lookupsAreStable() {
        HolderLookup.Provider provider = TestRegistries.lookup();
        assertSame(
                provider.lookup(Registries.ENCHANTMENT).orElseThrow(),
                provider.lookup(Registries.ENCHANTMENT).orElseThrow());
        assertSame(
                provider.lookup(Registries.ITEM).orElseThrow(),
                provider.lookup(Registries.ITEM).orElseThrow());
    }
}
