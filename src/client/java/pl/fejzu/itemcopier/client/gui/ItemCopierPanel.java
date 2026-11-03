package pl.fejzu.itemcopier.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import pl.fejzu.itemcopier.client.config.InsertMode;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;
import pl.fejzu.itemcopier.client.creative.CreativeItemRestorer;
import pl.fejzu.itemcopier.client.item.CopiedItem;
import pl.fejzu.itemcopier.client.item.ItemCopyHandler;
import pl.fejzu.itemcopier.client.mixin.AbstractContainerScreenAccessor;
import pl.fejzu.itemcopier.client.storage.ItemLibrary;
import pl.fejzu.itemcopier.client.util.Keys;
import pl.fejzu.itemcopier.client.util.Messages;

import java.util.List;
import java.util.Map;

public final class ItemCopierPanel {
    private static final long FLASH_MS = 1200L;
    private static final float HOVER_SPEED = 9.0f;
    private static final double DRAG_THRESHOLD = 3.0;

    private final ItemCopierConfig config;
    private final ItemLibrary library;
    private final ItemCopyHandler copyHandler;

    private final SearchField search = new SearchField();

    private PanelLayout layout;
    private float scale = 1.0f;
    private int scrollRow;
    private String selectedId;

    private CopiedItem dragged;
    private boolean dragging;

    private String hoveredId;
    private float hoverProgress;
    private long lastFrameNanos;

    private double pressX;
    private double pressY;

    private boolean renderFailed;

    public ItemCopierPanel(ItemCopierConfig config, ItemLibrary library, ItemCopyHandler copyHandler) {
        this.config = config;
        this.library = library;
        this.copyHandler = copyHandler;
    }

    public void reset() {
        dragged = null;
        dragging = false;
        hoveredId = null;
        hoverProgress = 0.0f;
        search.setFocused(false);
        lastFrameNanos = 0L;
    }

    public boolean isVisible() {
        return config.panelEnabled && !renderFailed && layout != null;
    }

    public void disableAfterFailure() {
        renderFailed = true;
        layout = null;
        dragged = null;
        dragging = false;
        search.setFocused(false);
    }

    public PanelLayout layout() {
        return layout;
    }

    public String searchQuery() {
        return search.value();
    }

    public void render(
            AbstractContainerScreen<?> screen,
            GuiGraphicsExtractor gfx,
            int mouseX,
            int mouseY,
            float tickDelta
    ) {
        if (!config.panelEnabled || renderFailed) {
            layout = null;
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        scale = config.panelScale;

        ScreenBounds bounds = ScreenBounds.of(screen).scaled(scale);
        layout = PanelLayout.compute(
                config,
                toPanelSpace(screen.width),
                toPanelSpace(screen.height),
                bounds.left(),
                bounds.top(),
                bounds.width(),
                bounds.height()
        );
        if (layout == null) {
            return;
        }

        library.reportLoadProblems();

        RegistryAccess registries = ItemCopyHandler.registries(client);
        List<CopiedItem> entries = library.view(search.value(), config.sorting());
        clampScroll(entries.size());
        advanceHoverAnimation();

        int localX = toPanelSpace(mouseX);
        int localY = toPanelSpace(mouseY);

        gfx.pose().pushMatrix();
        gfx.pose().scale(scale, scale);
        renderHeader(gfx, font, entries.size(), localX, localY);
        renderSearch(gfx, font, localX, localY);
        CopiedItem hovered = renderGrid(gfx, font, entries, registries, localX, localY);
        renderScrollbar(gfx, entries.size(), localX, localY);
        renderFooter(gfx, font, entries, hovered);
        gfx.pose().popMatrix();

        if (hovered != null) {
            renderTooltip(gfx, font, hovered, registries, mouseX, mouseY);
        }
        if (dragging && dragged != null && registries != null) {
            ItemStack stack = dragged.stack(registries);
            if (!stack.isEmpty()) {
                gfx.item(stack, mouseX - 8, mouseY - 8);
                gfx.itemDecorations(font, stack, mouseX - 8, mouseY - 8);
            }
        }
    }

    private void renderHeader(GuiGraphicsExtractor gfx, Font font, int shown, int mouseX, int mouseY) {
        int textY = layout.y() + GuiStyle.PADDING;
        gfx.text(font, Component.translatable("itemcopier.panel.title"),
                layout.x() + GuiStyle.PADDING, textY, GuiStyle.ACCENT_GOLD, true);

        int gearX = gearX();
        int gearY = gearY();
        boolean gearHovered = isOverGear(mouseX, mouseY);
        GuiStyle.gear(gfx, gearX, gearY,
                gearHovered ? GuiStyle.ACCENT_GOLD : GuiStyle.TEXT_MUTED);

        String counter = shown == library.size()
                ? String.valueOf(library.size())
                : shown + "/" + library.size();
        int counterWidth = font.width(counter);
        gfx.text(font, counter,
                gearX - 4 - counterWidth,
                textY, GuiStyle.TEXT_MUTED, true);
    }

    private void renderSearch(GuiGraphicsExtractor gfx, Font font, int mouseX, int mouseY) {
        search.setBounds(
                layout.x() + GuiStyle.PADDING,
                layout.y() + GuiStyle.PADDING + GuiStyle.HEADER_HEIGHT,
                layout.width() - 2 * GuiStyle.PADDING,
                GuiStyle.SEARCH_HEIGHT
        );
        search.render(gfx, font, mouseX, mouseY);
    }

    private CopiedItem renderGrid(
            GuiGraphicsExtractor gfx,
            Font font,
            List<CopiedItem> entries,
            RegistryAccess registries,
            int mouseX,
            int mouseY
    ) {
        int gridX = layout.gridX();
        int gridY = layout.gridY();
        CopiedItem hovered = null;

        if (entries.isEmpty()) {
            renderEmptyMessage(gfx, font);
            hoveredId = null;
            return null;
        }

        gfx.enableScissor(gridX, gridY, gridX + layout.gridWidth(), gridY + layout.gridHeight());

        int first = scrollRow * layout.columns();
        for (int cell = 0; cell < layout.capacity(); cell++) {
            int index = first + cell;
            if (index >= entries.size()) {
                break;
            }
            CopiedItem entry = entries.get(index);
            int col = cell % layout.columns();
            int row = cell / layout.columns();
            int slotX = gridX + col * GuiStyle.SLOT_SIZE;
            int slotY = gridY + row * GuiStyle.SLOT_SIZE;

            boolean isHovered = mouseX >= slotX && mouseX < slotX + GuiStyle.SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + GuiStyle.SLOT_SIZE;
            if (isHovered) {
                hovered = entry;
            }
            renderEntry(gfx, font, entry, registries, slotX, slotY, isHovered);
        }

        gfx.disableScissor();

        String newHoveredId = hovered != null ? hovered.id() : null;
        if (!java.util.Objects.equals(newHoveredId, hoveredId)) {
            hoveredId = newHoveredId;
            hoverProgress = 0.0f;
        }
        return hovered;
    }

    private void renderEntry(
            GuiGraphicsExtractor gfx,
            Font font,
            CopiedItem entry,
            RegistryAccess registries,
            int slotX,
            int slotY,
            boolean isHovered
    ) {
        ItemStack stack = entry.stack(registries);
        if (stack.isEmpty()) {
            GuiStyle.slotHighlight(gfx, slotX, slotY, GuiStyle.SLOT_BROKEN, GuiStyle.SLOT_BROKEN_BORDER);
            gfx.text(font, "?", slotX + 7, slotY + 5, GuiStyle.ACCENT_RED, true);
        } else {
            gfx.item(stack, slotX + 1, slotY + 1);
            gfx.itemDecorations(font, stack, slotX + 1, slotY + 1);
        }

        if (entry.id().equals(selectedId)) {
            GuiStyle.border(gfx, slotX, slotY, GuiStyle.SLOT_SIZE, GuiStyle.SLOT_SIZE,
                    GuiStyle.SLOT_SELECTED_BORDER);
        }

        long sinceCopy = System.currentTimeMillis() - copyHandler.lastCopiedAt();
        if (entry.id().equals(copyHandler.lastCopiedId()) && sinceCopy < FLASH_MS) {
            float fade = 1.0f - (sinceCopy / (float) FLASH_MS);
            GuiStyle.border(gfx, slotX, slotY, GuiStyle.SLOT_SIZE, GuiStyle.SLOT_SIZE,
                    GuiStyle.withAlpha(GuiStyle.ACCENT_GREEN, fade));
        }

        if (isHovered) {
            float eased = GuiStyle.ease(hoverProgress);
            gfx.fill(slotX + 1, slotY + 1, slotX + GuiStyle.SLOT_SIZE - 1, slotY + GuiStyle.SLOT_SIZE - 1,
                    GuiStyle.withAlpha(GuiStyle.SLOT_HOVER, eased));
            GuiStyle.border(gfx, slotX, slotY, GuiStyle.SLOT_SIZE, GuiStyle.SLOT_SIZE,
                    GuiStyle.withAlpha(GuiStyle.SLOT_HOVER_BORDER, eased));
            if (Keys.isShiftDown()) {
                GuiStyle.border(gfx, slotX, slotY, GuiStyle.SLOT_SIZE, GuiStyle.SLOT_SIZE,
                        GuiStyle.ACCENT_RED);
                gfx.text(font, "×", slotX + GuiStyle.SLOT_SIZE - 6, slotY + 1,
                        GuiStyle.ACCENT_RED, true);
            }
        }
    }

    private void renderEmptyMessage(GuiGraphicsExtractor gfx, Font font) {
        String keyName = pl.fejzu.itemcopier.client.input.CopyKeybinding.boundKeyName();
        Component message = Component.translatable("itemcopier.panel.empty", keyName);
        int available = layout.gridWidth();
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(message, available);
        int y = layout.gridY() + Math.max(0, (layout.gridHeight() - lines.size() * font.lineHeight) / 2);
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            gfx.centeredText(font, line, layout.gridX() + available / 2, y, GuiStyle.TEXT_MUTED);
            y += font.lineHeight;
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor gfx, int total, int mouseX, int mouseY) {
        int totalRows = totalRows(total);
        if (totalRows <= layout.visibleRows()) {
            return;
        }
        int trackX = layout.gridX() + layout.gridWidth() + 2;
        int trackY = layout.gridY();
        int trackHeight = layout.gridHeight();

        gfx.fill(trackX, trackY, trackX + GuiStyle.SCROLLBAR_WIDTH, trackY + trackHeight,
                GuiStyle.SCROLLBAR_TRACK_SOFT);

        int thumbHeight = Math.max(8, trackHeight * layout.visibleRows() / totalRows);
        int maxScroll = totalRows - layout.visibleRows();
        int thumbY = trackY + (trackHeight - thumbHeight) * scrollRow / Math.max(1, maxScroll);

        boolean hovered = mouseX >= trackX && mouseX < trackX + GuiStyle.SCROLLBAR_WIDTH
                && mouseY >= trackY && mouseY < trackY + trackHeight;
        gfx.fill(trackX, thumbY, trackX + GuiStyle.SCROLLBAR_WIDTH, thumbY + thumbHeight,
                hovered ? GuiStyle.SCROLLBAR_THUMB_HOVER : GuiStyle.SCROLLBAR_THUMB);
    }

    private void renderFooter(GuiGraphicsExtractor gfx, Font font, List<CopiedItem> entries, CopiedItem hovered) {
        int y = layout.y() + layout.height() - GuiStyle.PADDING - font.lineHeight + 1;

        String left;
        int leftColor;
        if (hovered != null && Keys.isShiftDown()) {
            left = "Shift + right click: delete";
            leftColor = GuiStyle.ACCENT_RED;
        } else if (hovered != null) {
            left = trim(font, hovered.plainDisplayName(), layout.width() - 2 * GuiStyle.PADDING - 40);
            leftColor = hovered.isBroken() ? GuiStyle.ACCENT_RED : GuiStyle.TEXT;
        } else if (CreativeItemRestorer.canRestore()) {
            left = "Creative";
            leftColor = GuiStyle.ACCENT_GREEN;
        } else {
            left = "View only";
            leftColor = GuiStyle.TEXT_DIM;
        }
        gfx.text(font, left, layout.x() + GuiStyle.PADDING, y, leftColor, true);

        if (hovered != null) {
            String hint = dropKeyName() + " ✕";
            gfx.text(font, hint,
                    layout.x() + layout.width() - GuiStyle.PADDING - font.width(hint),
                    y, GuiStyle.ACCENT_RED, true);
            return;
        }

        int totalRows = totalRows(entries.size());
        if (totalRows > layout.visibleRows()) {
            String pages = (scrollRow + 1) + "/" + (totalRows - layout.visibleRows() + 1);
            gfx.text(font, pages,
                    layout.x() + layout.width() - GuiStyle.PADDING - font.width(pages),
                    y, GuiStyle.TEXT_MUTED, true);
        }
    }

    private void renderTooltip(
            GuiGraphicsExtractor gfx,
            Font font,
            CopiedItem hovered,
            RegistryAccess registries,
            int mouseX,
            int mouseY
    ) {
        ItemStack stack = hovered.stack(registries);
        if (!stack.isEmpty()) {
            gfx.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            return;
        }
        gfx.setComponentTooltipForNextFrame(font, List.of(
                Component.literal(hovered.plainDisplayName()).withColor(GuiStyle.ACCENT_RED),
                Component.literal(hovered.itemId()).withColor(GuiStyle.TEXT_DIM),
                Component.literal("Cannot be read on this version:").withColor(GuiStyle.TEXT_MUTED),
                Component.literal(String.valueOf(hovered.decodeError())).withColor(GuiStyle.TEXT_DIM),
                Component.literal("The saved entry is kept untouched.").withColor(GuiStyle.TEXT_DIM)
        ), mouseX, mouseY);
    }

    public boolean mouseClicked(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        if (layout == null || !config.panelEnabled) {
            return false;
        }
        mouseX = toPanelSpace(mouseX);
        mouseY = toPanelSpace(mouseY);
        if (!layout.contains(mouseX, mouseY)) {
            if (search.isFocused()) {
                search.setFocused(false);
            }
            return false;
        }

        if (isOverGear(mouseX, mouseY)) {
            search.setFocused(false);
            Minecraft.getInstance().setScreenAndShow(
                    new ItemCopierSettingsScreen(screen, config));
            return true;
        }

        if (search.containsClearButton(mouseX, mouseY)) {
            search.clear();
            scrollRow = 0;
            return true;
        }
        if (search.contains(mouseX, mouseY)) {
            search.setFocused(true);
            return true;
        }
        search.setFocused(false);

        CopiedItem entry = entryAt(mouseX, mouseY);
        if (entry == null) {
            return true;
        }

        selectedId = entry.id();

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (Keys.isShiftDown()) {
                library.remove(entry);
                library.flush();
                if (entry.id().equals(selectedId)) {
                    selectedId = null;
                }
                Messages.send("deleted", Map.of("item", entry.plainDisplayName()));
                return true;
            }
            Minecraft.getInstance().setScreenAndShow(new ItemDetailsScreen(screen, library, entry));
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            InsertMode mode = config.insertion();
            switch (mode) {
                case DETAILS -> Minecraft.getInstance()
                        .setScreenAndShow(new ItemDetailsScreen(screen, library, entry));
                case DRAG_ONLY -> beginDrag(entry, mouseX, mouseY);
                case INSERT -> {
                    beginDrag(entry, mouseX, mouseY);
                }
            }
            return true;
        }
        return true;
    }

    public boolean mouseReleased(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        if (dragged == null) {
            return false;
        }
        mouseX = toPanelSpace(mouseX);
        mouseY = toPanelSpace(mouseY);
        CopiedItem entry = dragged;
        boolean moved = Math.abs(mouseX - pressX) > DRAG_THRESHOLD
                || Math.abs(mouseY - pressY) > DRAG_THRESHOLD;
        dragged = null;
        dragging = false;

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }

        if (moved && (layout == null || !layout.contains(mouseX, mouseY))) {
            Slot target = ((AbstractContainerScreenAccessor) screen).getHoveredSlot();
            if (target != null) {
                CreativeItemRestorer.Result result = CreativeItemRestorer.insertIntoSlot(entry, target);
                if (result == null) {
                    return true;
                }
                report(result, entry);
                return true;
            }
            return true;
        }

        if (config.insertion() == InsertMode.INSERT) {
            report(CreativeItemRestorer.insert(entry), entry);
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (layout == null || !layout.contains(toPanelSpace(mouseX), toPanelSpace(mouseY))) {
            return false;
        }
        int total = library.view(search.value(), config.sorting()).size();
        int maxScroll = Math.max(0, totalRows(total) - layout.visibleRows());
        scrollRow = Math.clamp(scrollRow - (int) Math.signum(amount), 0, maxScroll);
        return true;
    }

    public boolean keyPressed(int key) {
        if (layout == null) {
            return false;
        }
        if (search.isFocused()) {
            return search.keyPressed(key);
        }
        return false;
    }

    public boolean charTyped(int codepoint) {
        return layout != null && search.charTyped(codepoint);
    }

    public boolean deleteHovered() {
        if (layout == null || renderFailed || hoveredId == null || dragging) {
            return false;
        }
        CopiedItem entry = library.byId(hoveredId).orElse(null);
        if (entry == null) {
            return false;
        }
        library.remove(entry);
        library.flush();
        if (hoveredId.equals(selectedId)) {
            selectedId = null;
        }
        hoveredId = null;
        hoverProgress = 0.0f;
        Messages.send("deleted", Map.of("item", entry.plainDisplayName()));
        return true;
    }

    public void focusSearch() {
        search.setFocused(true);
    }

    private int toPanelSpace(int value) {
        return scale == 1.0f ? value : (int) (value / scale);
    }

    private double toPanelSpace(double value) {
        return scale == 1.0f ? value : value / scale;
    }

    private void beginDrag(CopiedItem entry, double mouseX, double mouseY) {
        dragged = entry;
        dragging = true;
        pressX = mouseX;
        pressY = mouseY;
    }

    private void report(CreativeItemRestorer.Result result, CopiedItem entry) {
        if (result.success()) {
            if (config.copyNotifications) {
                Messages.send("restore-ok", Map.of("item", entry.plainDisplayName()));
            }
            return;
        }
        if (result.detail() != null && !result.messageKey().equals("restore-no-permission")) {
            Messages.send(result.messageKey(), Map.of(
                    "error", result.detail(),
                    "item", entry.itemId()));
        } else {
            Messages.send(result.messageKey(), Map.of("item", entry.itemId()));
        }
    }

    private static String dropKeyName() {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null || client.options.keyDrop.isUnbound()) {
            return "Q";
        }
        return client.options.keyDrop.getTranslatedKeyMessage().getString();
    }

    private int gearX() {
        return layout.x() + layout.width() - GuiStyle.PADDING - GuiStyle.GEAR_SIZE;
    }

    private int gearY() {
        return layout.y() + GuiStyle.PADDING;
    }

    private boolean isOverGear(double mouseX, double mouseY) {
        if (layout == null) {
            return false;
        }
        int gearX = gearX();
        int gearY = gearY();
        return mouseX >= gearX - 2 && mouseX < gearX + GuiStyle.GEAR_SIZE + 2
                && mouseY >= gearY - 2 && mouseY < gearY + GuiStyle.GEAR_SIZE + 2;
    }

    private CopiedItem entryAt(double mouseX, double mouseY) {
        if (layout == null) {
            return null;
        }
        int gridX = layout.gridX();
        int gridY = layout.gridY();
        if (mouseX < gridX || mouseX >= gridX + layout.gridWidth()
                || mouseY < gridY || mouseY >= gridY + layout.gridHeight()) {
            return null;
        }
        int col = (int) ((mouseX - gridX) / GuiStyle.SLOT_SIZE);
        int row = (int) ((mouseY - gridY) / GuiStyle.SLOT_SIZE);
        int index = (scrollRow + row) * layout.columns() + col;

        List<CopiedItem> entries = library.view(search.value(), config.sorting());
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    private int totalRows(int entryCount) {
        if (layout == null || layout.columns() <= 0) {
            return 0;
        }
        return Math.max(1, (entryCount + layout.columns() - 1) / layout.columns());
    }

    private void clampScroll(int entryCount) {
        int maxScroll = Math.max(0, totalRows(entryCount) - layout.visibleRows());
        scrollRow = Math.clamp(scrollRow, 0, maxScroll);
    }

    private void advanceHoverAnimation() {
        long now = System.nanoTime();
        float seconds = lastFrameNanos == 0L ? 0.0f : (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;
        if (hoveredId == null) {
            hoverProgress = 0.0f;
            return;
        }
        hoverProgress = Math.min(1.0f, hoverProgress + seconds * HOVER_SPEED);
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }
}
