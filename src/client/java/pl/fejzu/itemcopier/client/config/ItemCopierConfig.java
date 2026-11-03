package pl.fejzu.itemcopier.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ItemCopierConfig {
    public static final int MIN_COLUMNS = 4;
    public static final int MAX_COLUMNS = 16;
    public static final int MIN_ROWS = 1;
    public static final int MAX_ROWS = 10;
    public static final int MAX_STRIP_LAST_LINES = 16;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean panelEnabled = true;
    public float panelScale = 1.0f;
    public int maxVisibleRows = 3;
    public int columns = 9;
    public boolean copyNotifications = true;
    public String duplicateMode = DuplicateMode.SKIP.id;
    public String insertMode = InsertMode.INSERT.id;
    public String sortOrder = SortOrder.NEWEST.id;
    public boolean recordSourceServer = true;
    public java.util.List<String> stripLorePatterns = new java.util.ArrayList<>();
    public int stripLastLoreLines = 0;
    public boolean debugLogging = false;
    public int maxLibrarySize = 2000;

    public DuplicateMode duplicates() {
        return DuplicateMode.fromId(duplicateMode);
    }

    public void setDuplicates(DuplicateMode mode) {
        this.duplicateMode = mode.id;
    }

    public InsertMode insertion() {
        return InsertMode.fromId(insertMode);
    }

    public void setInsertion(InsertMode mode) {
        this.insertMode = mode.id;
    }

    public SortOrder sorting() {
        return SortOrder.fromId(sortOrder);
    }

    public void setSorting(SortOrder order) {
        this.sortOrder = order.id;
    }

    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("itemcopier");
    }

    private static Path path() {
        return directory().resolve("config.json");
    }

    public static ItemCopierConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try {
                ItemCopierConfig config = GSON.fromJson(Files.readString(path), ItemCopierConfig.class);
                if (config != null) {
                    config.clamp();
                    ItemCopierLog.setDebugEnabled(config.debugLogging);
                    return config;
                }
            } catch (IOException | RuntimeException e) {
                ItemCopierLog.warn("Failed to read config.json, falling back to defaults: {}", e.toString());
            }
        }
        ItemCopierConfig fresh = new ItemCopierConfig();
        fresh.save();
        ItemCopierLog.setDebugEnabled(fresh.debugLogging);
        return fresh;
    }

    public java.util.List<String> lorePatterns() {
        return stripLorePatterns == null ? java.util.List.of() : stripLorePatterns;
    }

    public void clamp() {
        if (stripLorePatterns == null) {
            stripLorePatterns = new java.util.ArrayList<>();
        }
        columns = Math.clamp(columns, MIN_COLUMNS, MAX_COLUMNS);
        maxVisibleRows = Math.clamp(maxVisibleRows, MIN_ROWS, MAX_ROWS);
        panelScale = Math.clamp(panelScale, 0.5f, 1.5f);
        maxLibrarySize = Math.clamp(maxLibrarySize, 1, 100_000);
        stripLastLoreLines = Math.clamp(stripLastLoreLines, 0, MAX_STRIP_LAST_LINES);
    }

    public void save() {
        clamp();
        ItemCopierLog.setDebugEnabled(debugLogging);
        try {
            Files.createDirectories(directory());
            Path path = path();
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(this));
            try {
                Files.move(tmp, path,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ItemCopierLog.warn("Failed to save config.json: {}", e.toString());
        }
    }
}
