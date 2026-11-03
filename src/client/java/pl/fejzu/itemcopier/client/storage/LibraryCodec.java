package pl.fejzu.itemcopier.client.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import pl.fejzu.itemcopier.client.item.CopiedItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LibraryCodec {
    public static final int FORMAT_VERSION = 1;

    private static final String KEY_FORMAT = "format";
    private static final String KEY_ENTRIES = "entries";

    private static final String KEY_ID = "id";
    private static final String KEY_STACK = "stack";
    private static final String KEY_COPIED_AT = "copied_at";
    private static final String KEY_SERVER = "server";
    private static final String KEY_NAME = "name";
    private static final String KEY_ITEM_ID = "item_id";
    private static final String KEY_COUNT = "count";
    private static final String KEY_LABEL = "label";
    private static final String KEY_MC_VERSION = "mc_version";
    private static final String KEY_DATA_VERSION = "data_version";

    private LibraryCodec() {
    }

    public record LoadResult(List<CopiedItem> items, int skipped) {
    }

    public static CompoundTag write(List<CopiedItem> items) {
        CompoundTag root = new CompoundTag();
        root.putInt(KEY_FORMAT, FORMAT_VERSION);

        ListTag entries = new ListTag();
        for (CopiedItem item : items) {
            entries.add(writeEntry(item));
        }
        root.put(KEY_ENTRIES, entries);
        return root;
    }

    public static CompoundTag writeEntry(CopiedItem item) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_ID, item.id());
        tag.put(KEY_STACK, item.stackTag());
        tag.putLong(KEY_COPIED_AT, item.copiedAt().toEpochMilli());
        if (item.sourceServer() != null) {
            tag.putString(KEY_SERVER, item.sourceServer());
        }
        if (item.savedName() != null) {
            tag.putString(KEY_NAME, item.savedName());
        }
        if (item.itemId() != null) {
            tag.putString(KEY_ITEM_ID, item.itemId());
        }
        tag.putInt(KEY_COUNT, item.count());
        if (item.label() != null) {
            tag.putString(KEY_LABEL, item.label());
        }
        if (item.minecraftVersion() != null) {
            tag.putString(KEY_MC_VERSION, item.minecraftVersion());
        }
        tag.putInt(KEY_DATA_VERSION, item.dataVersion());
        return tag;
    }

    public static LoadResult read(CompoundTag root) {
        List<CopiedItem> items = new ArrayList<>();
        int skipped = 0;

        ListTag entries = root.getListOrEmpty(KEY_ENTRIES);
        for (int i = 0; i < entries.size(); i++) {
            Tag raw = entries.get(i);
            if (!(raw instanceof CompoundTag entry)) {
                skipped++;
                continue;
            }
            CopiedItem item = readEntry(entry);
            if (item == null) {
                skipped++;
                continue;
            }
            items.add(item);
        }
        return new LoadResult(items, skipped);
    }

    public static CopiedItem readEntry(CompoundTag entry) {
        CompoundTag stack = entry.getCompound(KEY_STACK).orElse(null);
        if (stack == null) {
            return null;
        }
        String id = entry.getStringOr(KEY_ID, "");
        if (id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        long copiedAt = entry.getLongOr(KEY_COPIED_AT, 0L);
        return new CopiedItem(
                id,
                stack,
                copiedAt > 0 ? Instant.ofEpochMilli(copiedAt) : Instant.EPOCH,
                entry.getString(KEY_SERVER).orElse(null),
                entry.getString(KEY_NAME).orElse(null),
                entry.getStringOr(KEY_ITEM_ID, stack.getStringOr("id", "minecraft:air")),
                Math.max(1, entry.getIntOr(KEY_COUNT, stack.getIntOr("count", 1))),
                entry.getString(KEY_LABEL).orElse(null),
                entry.getString(KEY_MC_VERSION).orElse(null),
                entry.getIntOr(KEY_DATA_VERSION, 0)
        );
    }
}
