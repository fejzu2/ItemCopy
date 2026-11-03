package pl.fejzu.itemcopier;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;

import java.util.List;
import java.util.stream.Stream;

public final class TestRegistries {
    private static HolderLookup.Provider lookup;
    private static RegistryAccess registryAccess;

    private TestRegistries() {
    }

    public static synchronized RegistryAccess registryAccess() {
        lookup();
        return registryAccess;
    }

    public static synchronized HolderLookup.Provider lookup() {
        if (lookup == null) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            Bootstrap.validate();

            RegistryAccess.Frozen builtIn =
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

            VanillaPackResources vanillaPack = ServerPacksSource.createVanillaPackSource();
            ResourceManager resources =
                    new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanillaPack));

            List<Registry.PendingTags<?>> pendingTags =
                    TagLoader.loadTagsForExistingRegistries(resources, builtIn);
            List<HolderLookup.RegistryLookup<?>> contextWithTags =
                    TagLoader.buildUpdatedLookups(builtIn, pendingTags);

            RegistryAccess.Frozen dynamic = RegistryDataLoader.load(
                    resources,
                    contextWithTags,
                    RegistryDataLoader.SYNCHRONIZED_REGISTRIES,
                    Runnable::run
            ).join();
            pendingTags.forEach(Registry.PendingTags::apply);

            HolderLookup.Provider provider = HolderLookup.Provider.create(
                    Stream.concat(builtIn.listRegistries(), dynamic.listRegistries()));

            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider)
                    .forEach(DataComponentInitializers.PendingComponents::apply);

            registryAccess = new RegistryAccess.ImmutableRegistryAccess(
                    Stream.concat(builtIn.registries(), dynamic.registries()));
            lookup = provider;
        }
        return lookup;
    }
}
