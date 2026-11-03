package pl.fejzu.itemcopier.client.storage;

import net.minecraft.nbt.CompoundTag;
import pl.fejzu.itemcopier.client.item.CopiedItem;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class LibraryTransfer {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String SINGLE_MARKER = "itemcopier_entry";

    private LibraryTransfer() {
    }

    public static Path exportSingle(CopiedItem item) throws IOException {
        CompoundTag root = LibraryCodec.writeEntry(item);
        root.putBoolean(SINGLE_MARKER, true);

        Path file = LibraryStorage.exportsDirectory()
                .resolve(fileName(item.plainDisplayName(), item.copiedAt().toEpochMilli()));
        LibraryStorage.writeTag(root, file);
        return file;
    }

    public static Path exportLibrary(List<CopiedItem> items) throws IOException {
        Path file = LibraryStorage.exportsDirectory()
                .resolve("library-" + STAMP.format(LocalDateTime.now()) + ".nbt");
        LibraryStorage.writeTag(LibraryCodec.write(items), file);
        return file;
    }

    public static List<CopiedItem> read(Path file) throws IOException {
        CompoundTag root = LibraryStorage.readTag(file);
        List<CopiedItem> found = new ArrayList<>();

        if (root.getList("entries").isPresent()) {
            LibraryCodec.LoadResult result = LibraryCodec.read(root);
            if (result.skipped() > 0) {
                ItemCopierLog.warn("Skipped {} unreadable entries while importing {}",
                        result.skipped(), file.getFileName());
            }
            found.addAll(result.items());
        } else {
            CopiedItem single = LibraryCodec.readEntry(root);
            if (single != null) {
                found.add(single);
            }
        }

        List<CopiedItem> withFreshIds = new ArrayList<>(found.size());
        for (CopiedItem item : found) {
            withFreshIds.add(item.withNewIdentity(item.copiedAt()));
        }
        return withFreshIds;
    }

    public static List<Path> importableFiles() {
        Path dir = LibraryStorage.exportsDirectory();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nbt"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            ItemCopierLog.warn("Could not list {}: {}", dir, e.toString());
            return List.of();
        }
    }

    private static String fileName(String displayName, long stamp) {
        String slug = displayName
                .replaceAll("[^A-Za-z0-9._ -]", "")
                .trim()
                .replace(' ', '_');
        if (slug.isBlank()) {
            slug = "item";
        }
        if (slug.length() > 48) {
            slug = slug.substring(0, 48);
        }
        return slug + "-" + stamp + ".nbt";
    }
}
