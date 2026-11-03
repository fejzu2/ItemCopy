package pl.fejzu.itemcopier.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;
import pl.fejzu.itemcopier.client.config.SortOrder;
import pl.fejzu.itemcopier.client.creative.CreativeItemRestorer;
import pl.fejzu.itemcopier.client.item.CopiedItem;
import pl.fejzu.itemcopier.client.item.ItemCopyHandler;
import pl.fejzu.itemcopier.client.storage.ItemLibrary;
import pl.fejzu.itemcopier.client.storage.LibraryTransfer;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;
import pl.fejzu.itemcopier.client.util.Messages;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemCopierScreen extends Screen {
    private static final int COLUMNS = 13;
    private static final int ROWS = 7;

    private final Screen parent;
    private final ItemCopierConfig config;
    private final ItemLibrary library;

    private EditBox searchBox;
    private Button sortButton;
    private Button insertButton;
    private Button detailsButton;
    private Button deleteButton;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int gridX;
    private int gridY;

    private int scrollRow;
    private String selectedId;
    private List<CopiedItem> entries = List.of();

    public ItemCopierScreen(Screen parent, ItemCopierConfig config, ItemLibrary library) {
        super(Component.translatable("itemcopier.screen.title"));
        this.parent = parent;
        this.config = config;
        this.library = library;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(COLUMNS * GuiStyle.SLOT_SIZE + 2 * 10 + GuiStyle.SCROLLBAR_WIDTH + 2,
                width - 20);
        panelHeight = Math.min(ROWS * GuiStyle.SLOT_SIZE + 94, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        gridX = panelX + 10;
        gridY = panelY + 44;

        int contentWidth = panelWidth - 20;
        searchBox = new EditBox(font, gridX, panelY + 24, contentWidth - 86, 16,
                Component.translatable("itemcopier.panel.search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("itemcopier.panel.search"));
        searchBox.setResponder(value -> {
            scrollRow = 0;
            refresh();
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            searchBox.setValue("");
            scrollRow = 0;
            refresh();
        }).bounds(gridX + contentWidth - 82, panelY + 24, 38, 16).build());

        sortButton = Button.builder(sortLabel(), b -> {
            config.setSorting(config.sorting().next());
            config.save();
            b.setMessage(sortLabel());
            refresh();
        }).bounds(gridX + contentWidth - 40, panelY + 24, 40, 16).build();
        addRenderableWidget(sortButton);

        int rowY = panelY + panelHeight - 40;
        int third = (contentWidth - 8) / 3;
        insertButton = Button.builder(Component.literal("Give to me"), b -> insertSelected())
                .bounds(gridX, rowY, third, 16).build();
        addRenderableWidget(insertButton);
        detailsButton = Button.builder(Component.literal("Details"), b -> openSelectedDetails())
                .bounds(gridX + third + 4, rowY, third, 16).build();
        addRenderableWidget(detailsButton);
        deleteButton = Button.builder(Component.literal("Delete"), b -> deleteSelected())
                .bounds(gridX + 2 * (third + 4), rowY, contentWidth - 2 * (third + 4), 16).build();
        addRenderableWidget(deleteButton);

        int bottomY = panelY + panelHeight - 20;
        int fifth = (contentWidth - 16) / 5;
        addRenderableWidget(Button.builder(Component.literal("Import"), b -> importAll())
                .bounds(gridX, bottomY, fifth, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Export all"), b -> exportAll())
                .bounds(gridX + fifth + 4, bottomY, fifth, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Clear all"), b -> confirmClearAll())
                .bounds(gridX + 2 * (fifth + 4), bottomY, fifth, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> minecraft.setScreenAndShow(
                new ItemCopierSettingsScreen(this, config)))
                .bounds(gridX + 3 * (fifth + 4), bottomY, fifth, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(gridX + 4 * (fifth + 4), bottomY, contentWidth - 4 * (fifth + 4), 16).build());

        refresh();
    }

    private Component sortLabel() {
        return Component.literal(switch (config.sorting()) {
            case NEWEST -> "New";
            case OLDEST -> "Old";
            case NAME -> "A-Z";
        });
    }

    private void refresh() {
        entries = library.view(searchBox != null ? searchBox.getValue() : "", config.sorting());
        int maxScroll = Math.max(0, totalRows() - ROWS);
        scrollRow = Math.clamp(scrollRow, 0, maxScroll);

        boolean hasSelection = selected() != null;
        insertButton.active = hasSelection && CreativeItemRestorer.canRestore()
                && !selected().isBroken();
        detailsButton.active = hasSelection;
        deleteButton.active = hasSelection;
    }

    private CopiedItem selected() {
        if (selectedId == null) {
            return null;
        }
        return library.byId(selectedId).orElse(null);
    }

    private int totalRows() {
        return Math.max(1, (entries.size() + COLUMNS - 1) / COLUMNS);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float tickDelta) {
        GuiStyle.panel(gfx, panelX, panelY, panelWidth, panelHeight);

        gfx.text(font, Component.translatable("itemcopier.panel.title"),
                panelX + 10, panelY + 9, GuiStyle.ACCENT_GOLD, false);
        String counter = entries.size() == library.size()
                ? library.size() + " entries"
                : entries.size() + " of " + library.size();
        gfx.text(font, counter,
                panelX + panelWidth - 10 - font.width(counter), panelY + 9,
                GuiStyle.TEXT_MUTED, false);
        GuiStyle.divider(gfx, panelX + 10, panelY + 20, panelWidth - 20);

        CopiedItem hovered = renderGrid(gfx, mouseX, mouseY);
        renderScrollbar(gfx);
        renderStatus(gfx, hovered);

        super.extractRenderState(gfx, mouseX, mouseY, tickDelta);

        if (hovered != null) {
            RegistryAccess registries = ItemCopyHandler.registries(minecraft);
            ItemStack stack = hovered.stack(registries);
            if (!stack.isEmpty()) {
                gfx.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            } else {
                gfx.setComponentTooltipForNextFrame(font, List.of(
                        Component.literal(hovered.plainDisplayName()).withColor(GuiStyle.ACCENT_RED),
                        Component.literal(hovered.itemId()).withColor(GuiStyle.TEXT_DIM),
                        Component.literal(String.valueOf(hovered.decodeError()))
                                .withColor(GuiStyle.TEXT_MUTED)
                ), mouseX, mouseY);
            }
        }
    }

    private CopiedItem renderGrid(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        if (entries.isEmpty()) {
            Component message = library.isEmpty()
                    ? Component.translatable("itemcopier.panel.empty",
                            pl.fejzu.itemcopier.client.input.CopyKeybinding.boundKeyName())
                    : Component.literal("Nothing matches that search.");
            gfx.centeredText(font, message, panelX + panelWidth / 2,
                    gridY + ROWS * GuiStyle.SLOT_SIZE / 2, GuiStyle.TEXT_DIM);
            return null;
        }

        RegistryAccess registries = ItemCopyHandler.registries(minecraft);
        CopiedItem hovered = null;
        int gridWidth = COLUMNS * GuiStyle.SLOT_SIZE;
        int gridHeight = ROWS * GuiStyle.SLOT_SIZE;

        gfx.enableScissor(gridX, gridY, gridX + gridWidth, gridY + gridHeight);
        int first = scrollRow * COLUMNS;
        for (int cell = 0; cell < COLUMNS * ROWS; cell++) {
            int index = first + cell;
            if (index >= entries.size()) {
                break;
            }
            CopiedItem entry = entries.get(index);
            int slotX = gridX + (cell % COLUMNS) * GuiStyle.SLOT_SIZE;
            int slotY = gridY + (cell / COLUMNS) * GuiStyle.SLOT_SIZE;
            boolean isHovered = mouseX >= slotX && mouseX < slotX + GuiStyle.SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + GuiStyle.SLOT_SIZE;
            if (isHovered) {
                hovered = entry;
            }

            GuiStyle.slot(gfx, slotX, slotY);
            ItemStack stack = entry.stack(registries);
            if (stack.isEmpty()) {
                GuiStyle.slotHighlight(gfx, slotX, slotY, GuiStyle.SLOT_BROKEN, GuiStyle.SLOT_BROKEN_BORDER);
                gfx.text(font, "?", slotX + 7, slotY + 5, GuiStyle.ACCENT_RED, false);
            } else {
                gfx.item(stack, slotX + 1, slotY + 1);
                gfx.itemDecorations(font, stack, slotX + 1, slotY + 1);
            }
            if (entry.id().equals(selectedId)) {
                GuiStyle.border(gfx, slotX, slotY, GuiStyle.SLOT_SIZE, GuiStyle.SLOT_SIZE,
                        GuiStyle.SLOT_SELECTED_BORDER);
            }
            if (isHovered) {
                GuiStyle.slotHighlight(gfx, slotX, slotY, GuiStyle.SLOT_HOVER, GuiStyle.SLOT_HOVER_BORDER);
            }
        }
        gfx.disableScissor();
        return hovered;
    }

    private void renderScrollbar(GuiGraphicsExtractor gfx) {
        int totalRows = totalRows();
        if (totalRows <= ROWS) {
            return;
        }
        int trackX = gridX + COLUMNS * GuiStyle.SLOT_SIZE + 2;
        int trackHeight = ROWS * GuiStyle.SLOT_SIZE;
        gfx.fill(trackX, gridY, trackX + GuiStyle.SCROLLBAR_WIDTH, gridY + trackHeight,
                GuiStyle.SCROLLBAR_TRACK);
        int thumbHeight = Math.max(10, trackHeight * ROWS / totalRows);
        int thumbY = gridY + (trackHeight - thumbHeight) * scrollRow / Math.max(1, totalRows - ROWS);
        gfx.fill(trackX, thumbY, trackX + GuiStyle.SCROLLBAR_WIDTH, thumbY + thumbHeight,
                GuiStyle.SCROLLBAR_THUMB);
    }

    private void renderStatus(GuiGraphicsExtractor gfx, CopiedItem hovered) {
        int y = panelY + panelHeight - 52;
        GuiStyle.divider(gfx, panelX + 10, y - 2, panelWidth - 20);

        CopiedItem shown = hovered != null ? hovered : selected();
        if (shown != null) {
            String name = shown.plainDisplayName();
            int maxWidth = panelWidth - 20 - 110;
            if (font.width(name) > maxWidth) {
                name = font.plainSubstrByWidth(name, maxWidth - font.width("...")) + "...";
            }
            gfx.text(font, name, panelX + 10, y + 2,
                    shown.isBroken() ? GuiStyle.ACCENT_RED : GuiStyle.TEXT, false);
        } else {
            gfx.text(font, "Click an item to select it", panelX + 10, y + 2, GuiStyle.TEXT_DIM, false);
        }

        String mode = CreativeItemRestorer.canRestore()
                ? "Creative: insertion enabled"
                : "Creative Mode or appropriate permissions required";
        int color = CreativeItemRestorer.canRestore() ? GuiStyle.ACCENT_GREEN : GuiStyle.TEXT_DIM;
        gfx.text(font, mode, panelX + panelWidth - 10 - font.width(mode), y + 2, color, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float tickDelta) {
        gfx.fill(0, 0, width, height, 0xC0000000);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        CopiedItem entry = entryAt(event.x(), event.y());
        if (entry != null) {
            selectedId = entry.id();
            refresh();
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                openSelectedDetails();
            } else if (doubleClick && CreativeItemRestorer.canRestore()) {
                insertSelected();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int maxScroll = Math.max(0, totalRows() - ROWS);
        if (maxScroll > 0) {
            scrollRow = Math.clamp(scrollRow - (int) Math.signum(vertical), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private CopiedItem entryAt(double mouseX, double mouseY) {
        int gridWidth = COLUMNS * GuiStyle.SLOT_SIZE;
        int gridHeight = ROWS * GuiStyle.SLOT_SIZE;
        if (mouseX < gridX || mouseX >= gridX + gridWidth
                || mouseY < gridY || mouseY >= gridY + gridHeight) {
            return null;
        }
        int col = (int) ((mouseX - gridX) / GuiStyle.SLOT_SIZE);
        int row = (int) ((mouseY - gridY) / GuiStyle.SLOT_SIZE);
        int index = (scrollRow + row) * COLUMNS + col;
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        library.flush();
        minecraft.setScreenAndShow(parent);
    }

    private void openSelectedDetails() {
        CopiedItem entry = selected();
        if (entry != null) {
            minecraft.setScreenAndShow(new ItemDetailsScreen(this, library, entry));
        }
    }

    private void insertSelected() {
        CopiedItem entry = selected();
        if (entry == null) {
            return;
        }
        CreativeItemRestorer.Result result = CreativeItemRestorer.insert(entry);
        if (result.success()) {
            Messages.send("restore-ok", Map.of("item", entry.plainDisplayName()));
        } else {
            Messages.send(result.messageKey(), Map.of(
                    "error", result.detail() != null ? result.detail() : "-",
                    "item", entry.itemId()));
        }
    }

    private void deleteSelected() {
        CopiedItem entry = selected();
        if (entry == null) {
            return;
        }
        library.remove(entry);
        library.flush();
        selectedId = null;
        Messages.send("deleted", Map.of("item", entry.plainDisplayName()));
        refresh();
    }

    private void confirmClearAll() {
        if (library.isEmpty()) {
            return;
        }
        int count = library.size();
        minecraft.setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        int removed = library.clear();
                        library.flush();
                        selectedId = null;
                        Messages.send("cleared", Map.of("count", String.valueOf(removed)));
                    }
                    minecraft.setScreenAndShow(this);
                    refresh();
                },
                Component.literal("Remove all " + count + " saved items?"),
                Component.literal("This cannot be undone. Export the library first if you want a backup.")
        ));
    }

    private void exportAll() {
        if (library.isEmpty()) {
            return;
        }
        try {
            Path file = LibraryTransfer.exportLibrary(library.all());
            Messages.send("export-ok", Map.of("file", file.toString()));
        } catch (IOException | RuntimeException e) {
            ItemCopierLog.error("Library export failed", e);
            Messages.send("export-failed", Map.of("error",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private void importAll() {
        List<Path> files = LibraryTransfer.importableFiles();
        if (files.isEmpty()) {
            Messages.send("import-nothing",
                    Map.of("dir", pl.fejzu.itemcopier.client.storage.LibraryStorage
                            .exportsDirectory().toString()));
            return;
        }
        int imported = 0;
        List<String> failures = new ArrayList<>();
        for (Path file : files) {
            try {
                for (CopiedItem item : LibraryTransfer.read(file)) {
                    if (library.addRaw(item)) {
                        imported++;
                    }
                }
            } catch (IOException | RuntimeException e) {
                failures.add(file.getFileName() + ": "
                        + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }
        library.flush();
        refresh();

        if (imported > 0) {
            Messages.send("import-ok", Map.of(
                    "count", String.valueOf(imported),
                    "file", files.size() == 1 ? files.getFirst().getFileName().toString()
                            : files.size() + " files"));
        }
        if (!failures.isEmpty()) {
            Messages.send("import-failed", Map.of("error", String.join("; ", failures)));
        }
    }
}
