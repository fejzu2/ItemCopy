package pl.fejzu.itemcopier.client.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import pl.fejzu.itemcopier.client.serialization.ItemStackSerializer;

import java.time.Instant;
import java.util.UUID;

public final class CopiedItem {
    private final String id;
    private final CompoundTag stackTag;
    private final Instant copiedAt;
    private final String sourceServer;
    private final String savedName;
    private final String itemId;
    private final int count;
    private final String minecraftVersion;
    private final int dataVersion;

    private String label;

    private ItemStack decoded = ItemStack.EMPTY;
    private String decodeError;
    private HolderLookup.Provider decodedWith;

    public CopiedItem(
            String id,
            CompoundTag stackTag,
            Instant copiedAt,
            String sourceServer,
            String savedName,
            String itemId,
            int count,
            String label,
            String minecraftVersion,
            int dataVersion
    ) {
        this.id = id;
        this.stackTag = stackTag;
        this.copiedAt = copiedAt;
        this.sourceServer = sourceServer;
        this.savedName = savedName;
        this.itemId = itemId;
        this.count = count;
        this.label = label;
        this.minecraftVersion = minecraftVersion;
        this.dataVersion = dataVersion;
    }

    public static CopiedItem capture(
            ItemStack stack,
            HolderLookup.Provider registries,
            String sourceServer,
            String minecraftVersion,
            int dataVersion
    ) throws ItemStackSerializer.SerializationException {
        CompoundTag tag = ItemStackSerializer.encode(stack, registries);
        CopiedItem item = new CopiedItem(
                UUID.randomUUID().toString(),
                tag,
                Instant.now(),
                sourceServer,
                stack.getHoverName().getString(),
                ItemStackSerializer.registryId(stack),
                stack.getCount(),
                null,
                minecraftVersion,
                dataVersion
        );
        item.decoded = stack.copy();
        item.decodedWith = registries;
        return item;
    }

    public String id() {
        return id;
    }

    public CompoundTag stackTag() {
        return stackTag;
    }

    public Instant copiedAt() {
        return copiedAt;
    }

    public String sourceServer() {
        return sourceServer;
    }

    public String savedName() {
        return savedName;
    }

    public String itemId() {
        return itemId;
    }

    public int count() {
        return count;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = (label == null || label.isBlank()) ? null : label;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public int dataVersion() {
        return dataVersion;
    }

    public ItemStack stack(HolderLookup.Provider registries) {
        if (registries == null) {
            return decoded;
        }
        if (decodedWith == registries && (decodeError == null || !decoded.isEmpty())) {
            return decoded;
        }
        decodedWith = registries;
        try {
            decoded = ItemStackSerializer.decode(stackTag, registries);
            decodeError = null;
        } catch (ItemStackSerializer.SerializationException e) {
            decoded = ItemStack.EMPTY;
            decodeError = e.getMessage();
        }
        return decoded;
    }

    public ItemStack cachedStack() {
        return decoded;
    }

    public String decodeError() {
        return decodeError;
    }

    public boolean isBroken() {
        return decodeError != null;
    }

    public String plainDisplayName() {
        if (label != null) {
            return label;
        }
        if (!decoded.isEmpty()) {
            return decoded.getHoverName().getString();
        }
        return savedName != null ? savedName : itemId;
    }

    public Component displayName() {
        if (label != null) {
            return Component.literal(label);
        }
        if (!decoded.isEmpty()) {
            return decoded.getHoverName();
        }
        return Component.literal(plainDisplayName());
    }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(java.util.Locale.ROOT).trim();
        if (plainDisplayName().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
            return true;
        }
        if (savedName != null && savedName.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
            return true;
        }
        if (itemId != null && itemId.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
            return true;
        }
        return sourceServer != null && sourceServer.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    public CopiedItem withStackTag(CompoundTag newTag) {
        CopiedItem replacement = new CopiedItem(
                id,
                newTag,
                copiedAt,
                sourceServer,
                savedName,
                itemId,
                count,
                label,
                minecraftVersion,
                dataVersion
        );
        return replacement;
    }

    public CopiedItem withNewIdentity(Instant when) {
        CopiedItem copy = new CopiedItem(
                UUID.randomUUID().toString(),
                stackTag.copy(),
                when,
                sourceServer,
                savedName,
                itemId,
                count,
                label,
                minecraftVersion,
                dataVersion
        );
        copy.decoded = decoded;
        copy.decodedWith = decodedWith;
        copy.decodeError = decodeError;
        return copy;
    }

    public boolean hasSamePayloadAs(CopiedItem other) {
        return other != null && stackTag.equals(other.stackTag);
    }

    public boolean hasSamePayloadAs(Tag otherTag) {
        return stackTag.equals(otherTag);
    }
}
