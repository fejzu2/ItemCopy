package pl.fejzu.itemcopier.client.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;
import pl.fejzu.itemcopier.client.item.CopiedItem;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class LibraryStorage {
    private static final String FILE_NAME = "library.nbt";
    private static final long READ_LIMIT_BYTES = 64L * 1024 * 1024;

    private LibraryStorage() {
    }

    public static Path directory() {
        return ItemCopierConfig.directory();
    }

    public static Path libraryPath() {
        return directory().resolve(FILE_NAME);
    }

    public static Path exportsDirectory() {
        return directory().resolve("exports");
    }

    public record LoadOutcome(List<CopiedItem> items, int skipped, String error, Path backup) {
    }

    public static LoadOutcome load() {
        Path path = libraryPath();
        if (!Files.exists(path)) {
            return new LoadOutcome(List.of(), 0, null, null);
        }
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(READ_LIMIT_BYTES));
            LibraryCodec.LoadResult result = LibraryCodec.read(root);
            ItemCopierLog.debug("Loaded {} library entries ({} skipped)",
                    result.items().size(), result.skipped());
            return new LoadOutcome(result.items(), result.skipped(), null, null);
        } catch (IOException | RuntimeException e) {
            Path backup = quarantine(path);
            ItemCopierLog.error("Could not read " + path + " - kept it as " + backup, e);
            return new LoadOutcome(List.of(), 0,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), backup);
        }
    }

    private static Path quarantine(Path path) {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        Path backup = path.resolveSibling(FILE_NAME + ".broken-" + stamp);
        try {
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            ItemCopierLog.warn("Could not move the broken library aside: {}", e.toString());
            return path;
        }
    }

    public static void save(List<CopiedItem> items) throws IOException {
        Files.createDirectories(directory());
        Path path = libraryPath();
        Path tmp = path.resolveSibling(FILE_NAME + ".tmp");

        NbtIo.writeCompressed(LibraryCodec.write(items), tmp);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
        ItemCopierLog.debug("Saved {} library entries to {}", items.size(), path);
    }

    public static CompoundTag readTag(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.create(READ_LIMIT_BYTES));
    }

    public static void writeTag(CompoundTag tag, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        NbtIo.writeCompressed(tag, path);
    }
}
