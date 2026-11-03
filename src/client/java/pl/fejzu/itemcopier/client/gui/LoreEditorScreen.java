package pl.fejzu.itemcopier.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import pl.fejzu.itemcopier.client.ItemCopierClient;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;
import pl.fejzu.itemcopier.client.item.CopiedItem;
import pl.fejzu.itemcopier.client.item.ItemCopyHandler;
import pl.fejzu.itemcopier.client.item.LoreEditor;
import pl.fejzu.itemcopier.client.serialization.ItemStackSerializer;
import pl.fejzu.itemcopier.client.storage.ItemLibrary;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;
import pl.fejzu.itemcopier.client.util.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LoreEditorScreen extends Screen {

    private static final int ROW_HEIGHT = 12;
    private static final int VISIBLE_ROWS = 12;

    private final Screen parent;
    private final ItemLibrary library;
    private final CopiedItem entry;

    private final List<Component> lines = new ArrayList<>();
    private final List<Boolean> kept = new ArrayList<>();

    private Button rememberButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listX;
    private int listY;
    private int listWidth;
    private int scrollRow;

    public LoreEditorScreen(Screen parent, ItemLibrary library, CopiedItem entry) {
        super(Component.literal("ItemCopier - Lore"));
        this.parent = parent;
        this.library = library;
        this.entry = entry;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(340, width - 20);
        panelHeight = Math.min(ROW_HEIGHT * VISIBLE_ROWS + 94, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        listX = panelX + 10;
        listY = panelY + 36;
        listWidth = panelWidth - 20;

        if (lines.isEmpty()) {
            HolderLookup.Provider registries = ItemCopyHandler.registries(minecraft);
            ItemStack stack = entry.stack(registries);
            lines.addAll(LoreEditor.linesOf(stack));
            for (int i = 0; i < lines.size(); i++) {
                kept.add(Boolean.TRUE);
            }
        }

        int contentWidth = panelWidth - 20;
        int topY = panelY + panelHeight - 54;
        int third = (contentWidth - 8) / 3;

        addRenderableWidget(Button.builder(Component.literal("Keep all"), b -> setAll(true))
                .bounds(listX, topY, third, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Remove all"), b -> setAll(false))
                .bounds(listX + third + 4, topY, third, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Wytnij puste"), b -> dropBlanks())
                .bounds(listX + 2 * (third + 4), topY, contentWidth - 2 * (third + 4), 14)
                .tooltip(Tooltip.create(Component.literal(
                        "Odznacza puste linijki - zwykle zostaja po wycietym tekscie.")))
                .build());

        int rememberY = panelY + panelHeight - 36;
        rememberButton = Button.builder(Component.literal("Zawsze wycinaj takie linijki"),
                        b -> rememberRemoved())
                .bounds(listX, rememberY, contentWidth, 14)
                .tooltip(Tooltip.create(Component.literal(
                        "Dopisuje wzorzec do filtrow lore, zeby nastepne kopie byly juz czyste.")))
                .build();
        addRenderableWidget(rememberButton);

        int bottomY = panelY + panelHeight - 18;
        int half = (contentWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(listX, bottomY, half, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(listX + half + 4, bottomY, contentWidth - half - 4, 14).build());
    }

    private void setAll(boolean value) {
        kept.replaceAll(ignored -> value);
    }

    private void dropBlanks() {
        for (int i = 0; i < lines.size(); i++) {
            if (LoreEditor.isBlank(lines.get(i))) {
                kept.set(i, Boolean.FALSE);
            }
        }
    }

    private int removedCount() {
        int removed = 0;
        for (Boolean keep : kept) {
            if (!keep) {
                removed++;
            }
        }
        return removed;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float tickDelta) {
        GuiStyle.panel(gfx, panelX, panelY, panelWidth, panelHeight);

        gfx.text(font, "LORE", panelX + 10, panelY + 9, GuiStyle.ACCENT_GOLD, false);
        String counter = (lines.size() - removedCount()) + " / " + lines.size() + " kept";
        gfx.text(font, counter, panelX + panelWidth - 10 - font.width(counter), panelY + 9,
                GuiStyle.TEXT_MUTED, false);
        gfx.text(font, "Click a line to keep or drop it", panelX + 10, panelY + 21,
                GuiStyle.TEXT_DIM, false);
        GuiStyle.divider(gfx, panelX + 10, panelY + 32, panelWidth - 20);

        if (lines.isEmpty()) {
            gfx.centeredText(font, Component.literal("This item has no lore."),
                    panelX + panelWidth / 2, listY + 20, GuiStyle.TEXT_DIM);
            super.extractRenderState(gfx, mouseX, mouseY, tickDelta);
            return;
        }

        int listHeight = VISIBLE_ROWS * ROW_HEIGHT;
        gfx.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scrollRow + row;
            if (index >= lines.size()) {
                break;
            }
            int rowY = listY + row * ROW_HEIGHT;
            boolean hovered = mouseX >= listX && mouseX < listX + listWidth
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean keep = kept.get(index);

            if (hovered) {
                gfx.fill(listX, rowY, listX + listWidth, rowY + ROW_HEIGHT, 0x22FFFFFF);
            }
            gfx.text(font, keep ? "✔" : "✘", listX + 2, rowY + 2,
                    keep ? GuiStyle.ACCENT_GREEN : GuiStyle.ACCENT_RED, false);

            Component line = lines.get(index);
            if (LoreEditor.isBlank(line)) {
                String marker = "(pusta linia)";
                gfx.text(font, marker, listX + 14, rowY + 2,
                        keep ? GuiStyle.TEXT_DIM : GuiStyle.ACCENT_RED, false);
                if (!keep) {
                    gfx.fill(listX + 14, rowY + 6, listX + 14 + font.width(marker), rowY + 7,
                            GuiStyle.ACCENT_RED);
                }
            } else if (keep) {
                gfx.text(font, line, listX + 14, rowY + 2, GuiStyle.TEXT, false);
            } else {
                gfx.text(font, line.getString(), listX + 14, rowY + 2, GuiStyle.TEXT_DIM, false);
                int strikeWidth = Math.min(font.width(line.getString()), listWidth - 18);
                gfx.fill(listX + 14, rowY + 6, listX + 14 + strikeWidth, rowY + 7, GuiStyle.ACCENT_RED);
            }
        }
        gfx.disableScissor();

        if (lines.size() > VISIBLE_ROWS) {
            int trackX = listX + listWidth - 3;
            gfx.fill(trackX, listY, trackX + 3, listY + listHeight, GuiStyle.SCROLLBAR_TRACK);
            int thumbHeight = Math.max(10, listHeight * VISIBLE_ROWS / lines.size());
            int thumbY = listY + (listHeight - thumbHeight) * scrollRow
                    / Math.max(1, lines.size() - VISIBLE_ROWS);
            gfx.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, GuiStyle.SCROLLBAR_THUMB);
        }

        super.extractRenderState(gfx, mouseX, mouseY, tickDelta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float tickDelta) {
        gfx.fill(0, 0, width, height, 0xC0000000);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int listHeight = VISIBLE_ROWS * ROW_HEIGHT;
        if (event.x() >= listX && event.x() < listX + listWidth
                && event.y() >= listY && event.y() < listY + listHeight) {
            int row = (int) ((event.y() - listY) / ROW_HEIGHT);
            int index = scrollRow + row;
            if (index >= 0 && index < kept.size()) {
                kept.set(index, !kept.get(index));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int maxScroll = Math.max(0, lines.size() - VISIBLE_ROWS);
        if (maxScroll > 0) {
            scrollRow = Math.clamp(scrollRow - (int) Math.signum(vertical), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private void rememberRemoved() {
        ItemCopierConfig config = ItemCopierClient.config();
        if (config == null) {
            return;
        }
        List<String> patterns = config.stripLorePatterns;
        int added = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (kept.get(i)) {
                continue;
            }
            String suggestion = LoreEditor.suggestPattern(lines.get(i).getString());
            if (suggestion.isEmpty() || containsIgnoreCase(patterns, suggestion)) {
                continue;
            }
            patterns.add(suggestion);
            added++;
        }
        if (added == 0) {
            Messages.send("lore-pattern-none");
            return;
        }
        config.save();
        Messages.send("lore-pattern-added", Map.of("count", String.valueOf(added)));
    }

    private static boolean containsIgnoreCase(List<String> patterns, String value) {
        for (String pattern : patterns) {
            if (pattern.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private void apply() {
        if (removedCount() == 0) {
            onClose();
            return;
        }
        HolderLookup.Provider registries = ItemCopyHandler.registries(minecraft);
        if (registries == null) {
            Messages.send("lore-edit-failed", Map.of("error", "no registry context"));
            return;
        }
        ItemStack stack = entry.stack(registries);
        if (stack.isEmpty()) {
            Messages.send("lore-edit-failed", Map.of("error", "item could not be read"));
            return;
        }

        List<Component> keptLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (kept.get(i)) {
                keptLines.add(lines.get(i));
            }
        }

        try {
            CompoundTag updated = LoreEditor.reencodeWithLore(stack, keptLines, registries);
            int removed = removedCount();
            library.replaceStack(entry, updated);
            library.flush();
            Messages.send("lore-edited", Map.of(
                    "removed", String.valueOf(removed),
                    "kept", String.valueOf(keptLines.size())));
        } catch (ItemStackSerializer.SerializationException | RuntimeException e) {
            ItemCopierLog.error("Could not re-encode the item after editing its lore", e);
            Messages.send("lore-edit-failed", Map.of("error",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
