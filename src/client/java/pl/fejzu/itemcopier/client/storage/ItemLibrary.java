package pl.fejzu.itemcopier.client.storage;

import net.minecraft.core.HolderLookup;
import pl.fejzu.itemcopier.client.config.DuplicateMode;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;
import pl.fejzu.itemcopier.client.config.SortOrder;
import pl.fejzu.itemcopier.client.item.CopiedItem;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;
import pl.fejzu.itemcopier.client.util.Messages;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemLibrary {
    private final ItemCopierConfig config;
    private final List<CopiedItem> items = new ArrayList<>();

    private boolean dirty;
    private int brokenOnLoad;
    private boolean reportedBroken;

    private int revision;
    private List<CopiedItem> cachedView;
    private String cachedViewQuery;
    private SortOrder cachedViewOrder;
    private int cachedViewRevision = -1;

    public ItemLibrary(ItemCopierConfig config) {
        this.config = config;
    }

    public enum AddOutcome {
        ADDED,
        DUPLICATE_SKIPPED,
        DUPLICATE_REFRESHED,
        LIBRARY_FULL
    }

    public void loadFromDisk() {
        LibraryStorage.LoadOutcome outcome = LibraryStorage.load();
        items.clear();
        items.addAll(outcome.items());
        touch();
        brokenOnLoad = outcome.skipped();
        reportedBroken = false;
        dirty = false;

        if (outcome.error() != null) {
            Messages.send("library-load-failed", Map.of(
                    "error", outcome.error(),
                    "backup", outcome.backup() != null ? outcome.backup().getFileName().toString() : "-"
            ));
        }
    }

    public void reportLoadProblems() {
        if (brokenOnLoad > 0 && !reportedBroken) {
            reportedBroken = true;
            Messages.send("library-entry-broken", Map.of("count", String.valueOf(brokenOnLoad)));
        }
    }

    public List<CopiedItem> all() {
        return Collections.unmodifiableList(items);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Optional<CopiedItem> byId(String id) {
        return items.stream().filter(item -> item.id().equals(id)).findFirst();
    }

    public List<CopiedItem> view(String query, SortOrder order) {
        String key = query == null ? "" : query;
        if (cachedView != null
                && cachedViewRevision == revision
                && key.equals(cachedViewQuery)
                && order == cachedViewOrder) {
            return cachedView;
        }

        List<CopiedItem> filtered = new ArrayList<>();
        for (CopiedItem item : items) {
            if (item.matches(key)) {
                filtered.add(item);
            }
        }
        filtered.sort(order.comparator());

        cachedView = Collections.unmodifiableList(filtered);
        cachedViewQuery = key;
        cachedViewOrder = order;
        cachedViewRevision = revision;
        return cachedView;
    }

    private void touch() {
        revision++;
        cachedView = null;
    }

    public AddOutcome add(CopiedItem item) {
        CopiedItem existing = null;
        for (CopiedItem candidate : items) {
            if (candidate.hasSamePayloadAs(item)) {
                existing = candidate;
                break;
            }
        }

        if (existing != null) {
            DuplicateMode mode = config.duplicates();
            if (mode == DuplicateMode.SKIP) {
                return AddOutcome.DUPLICATE_SKIPPED;
            }
            if (mode == DuplicateMode.REFRESH) {
                int index = items.indexOf(existing);
                CopiedItem refreshed = existing.withNewIdentity(Instant.now());
                refreshed.setLabel(existing.label());
                items.set(index, refreshed);
                dirty = true;
                touch();
                return AddOutcome.DUPLICATE_REFRESHED;
            }
        }

        if (items.size() >= config.maxLibrarySize) {
            return AddOutcome.LIBRARY_FULL;
        }
        items.add(item);
        dirty = true;
        touch();
        return AddOutcome.ADDED;
    }

    public boolean addRaw(CopiedItem item) {
        if (items.size() >= config.maxLibrarySize) {
            return false;
        }
        items.add(item);
        dirty = true;
        touch();
        return true;
    }

    public boolean remove(CopiedItem item) {
        boolean removed = items.remove(item);
        if (removed) {
            dirty = true;
            touch();
        }
        return removed;
    }

    public int clear() {
        int count = items.size();
        items.clear();
        dirty = true;
        touch();
        return count;
    }

    public CopiedItem replaceStack(CopiedItem item, net.minecraft.nbt.CompoundTag newTag) {
        int index = items.indexOf(item);
        if (index < 0) {
            return item;
        }
        CopiedItem replacement = item.withStackTag(newTag);
        items.set(index, replacement);
        dirty = true;
        touch();
        return replacement;
    }

    public void rename(CopiedItem item, String label) {
        item.setLabel(label);
        dirty = true;
        touch();
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void flush() {
        if (!dirty) {
            return;
        }
        try {
            LibraryStorage.save(items);
            dirty = false;
        } catch (IOException | RuntimeException e) {
            ItemCopierLog.error("Failed to save the item library", e);
            Messages.send("library-save-failed", Map.of(
                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    public void resolveAll(HolderLookup.Provider registries) {
        if (registries == null) {
            return;
        }
        int broken = 0;
        for (CopiedItem item : items) {
            item.stack(registries);
            if (item.isBroken()) {
                broken++;
            }
        }
        if (broken > 0) {
            ItemCopierLog.debug("{} of {} library entries could not be decoded in this registry context",
                    broken, items.size());
        }
    }
}
