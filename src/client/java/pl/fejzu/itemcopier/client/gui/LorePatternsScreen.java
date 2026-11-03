package pl.fejzu.itemcopier.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;

import java.util.ArrayList;
import java.util.List;

public final class LorePatternsScreen extends Screen {

    private static final int ROW_HEIGHT = 14;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final String TAIL_HELP =
            "Ucina tyle ostatnich linijek lore przy kopiowaniu, bez patrzenia na tresc. 0 wylacza.";

    private final Screen parent;
    private final ItemCopierConfig config;

    private EditBox input;
    private Button addButton;
    private Button dropLessButton;
    private Button dropMoreButton;
    private int tailRowY;
    private int tailValueX;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listX;
    private int listY;
    private int listWidth;
    private int visibleRows;
    private int scrollRow;

    public LorePatternsScreen(Screen parent, ItemCopierConfig config) {
        super(Component.literal("ItemCopier - Lore filters"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(330, width - 20);
        panelHeight = Math.min(ROW_HEIGHT * MAX_VISIBLE_ROWS + 116, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        listX = panelX + 10;
        listY = panelY + 44;
        listWidth = panelWidth - 20;
        visibleRows = Math.clamp((panelHeight - 116) / ROW_HEIGHT, 1, MAX_VISIBLE_ROWS);
        scrollRow = Math.clamp(scrollRow, 0, Math.max(0, patterns().size() - visibleRows));

        int contentWidth = panelWidth - 20;
        int inputY = panelY + panelHeight - 38;
        tailRowY = panelY + panelHeight - 60;
        tailValueX = listX + contentWidth - 30;

        dropLessButton = Button.builder(Component.literal("-"), b -> changeDropLast(-1))
                .bounds(listX + contentWidth - 60, tailRowY, 16, 16)
                .tooltip(Tooltip.create(Component.literal(TAIL_HELP)))
                .build();
        addRenderableWidget(dropLessButton);

        dropMoreButton = Button.builder(Component.literal("+"), b -> changeDropLast(1))
                .bounds(listX + contentWidth - 16, tailRowY, 16, 16)
                .tooltip(Tooltip.create(Component.literal(TAIL_HELP)))
                .build();
        addRenderableWidget(dropMoreButton);
        refreshDropButtons();

        input = new EditBox(font, listX, inputY, contentWidth - 52, 16,
                Component.literal("Pattern"));
        input.setMaxLength(64);
        input.setHint(Component.literal("np. Cena:"));
        input.setResponder(value -> refreshAddButton());
        addRenderableWidget(input);
        setInitialFocus(input);

        addButton = Button.builder(Component.literal("Dodaj"), b -> addCurrent())
                .bounds(listX + contentWidth - 48, inputY, 48, 16).build();
        addRenderableWidget(addButton);
        refreshAddButton();

        addRenderableWidget(Button.builder(Component.literal("Gotowe"), b -> onClose())
                .bounds(listX, panelY + panelHeight - 18, contentWidth, 14).build());
    }

    private List<String> patterns() {
        if (config.stripLorePatterns == null) {
            config.stripLorePatterns = new ArrayList<>();
        }
        return config.stripLorePatterns;
    }

    private void changeDropLast(int delta) {
        config.stripLastLoreLines = Math.clamp(
                config.stripLastLoreLines + delta, 0, ItemCopierConfig.MAX_STRIP_LAST_LINES);
        config.save();
        refreshDropButtons();
    }

    private void refreshDropButtons() {
        dropLessButton.active = config.stripLastLoreLines > 0;
        dropMoreButton.active = config.stripLastLoreLines < ItemCopierConfig.MAX_STRIP_LAST_LINES;
    }

    private void refreshAddButton() {
        String value = input == null ? "" : input.getValue().trim();
        addButton.active = !value.isEmpty() && !containsIgnoreCase(value);
    }

    private boolean containsIgnoreCase(String value) {
        for (String pattern : patterns()) {
            if (pattern.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private void addCurrent() {
        String value = input.getValue().trim();
        if (value.isEmpty() || containsIgnoreCase(value)) {
            return;
        }
        patterns().add(value);
        config.save();
        input.setValue("");
        scrollRow = Math.max(0, patterns().size() - visibleRows);
        refreshAddButton();
    }

    private void remove(int index) {
        if (index < 0 || index >= patterns().size()) {
            return;
        }
        patterns().remove(index);
        config.save();
        int maxScroll = Math.max(0, patterns().size() - visibleRows);
        scrollRow = Math.clamp(scrollRow, 0, maxScroll);
        refreshAddButton();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float tickDelta) {
        GuiStyle.panel(gfx, panelX, panelY, panelWidth, panelHeight);

        gfx.text(font, "LORE FILTERS", panelX + 10, panelY + 9, GuiStyle.ACCENT_GOLD, false);
        String counter = patterns().size() + "";
        gfx.text(font, counter, panelX + panelWidth - 10 - font.width(counter), panelY + 9,
                GuiStyle.TEXT_MUTED, false);
        gfx.text(font, "Linijki lore zawierajace ten tekst nie beda kopiowane",
                panelX + 10, panelY + 21, GuiStyle.TEXT_DIM, false);
        gfx.text(font, "Wielkosc liter nie ma znaczenia",
                panelX + 10, panelY + 31, GuiStyle.TEXT_DIM, false);
        GuiStyle.divider(gfx, panelX + 10, panelY + 41, panelWidth - 20);

        GuiStyle.divider(gfx, panelX + 10, panelY + panelHeight - 66, panelWidth - 20);
        gfx.text(font, "Wycinaj ostatnie linijki", panelX + 10, tailRowY + 4, GuiStyle.TEXT, false);
        String tail = String.valueOf(config.stripLastLoreLines);
        gfx.text(font, tail, tailValueX - font.width(tail) / 2, tailRowY + 4,
                config.stripLastLoreLines > 0 ? GuiStyle.ACCENT_GREEN : GuiStyle.TEXT_DIM, false);

        List<String> patterns = patterns();
        int listHeight = visibleRows * ROW_HEIGHT;

        if (patterns.isEmpty()) {
            gfx.centeredText(font, Component.literal("Brak filtrow - kopiowane jest cale lore."),
                    panelX + panelWidth / 2, listY + 24, GuiStyle.TEXT_DIM);
            super.extractRenderState(gfx, mouseX, mouseY, tickDelta);
            return;
        }

        gfx.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        for (int row = 0; row < visibleRows; row++) {
            int index = scrollRow + row;
            if (index >= patterns.size()) {
                break;
            }
            int rowY = listY + row * ROW_HEIGHT;
            boolean overRow = mouseX >= listX && mouseX < listX + listWidth
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean overRemove = overRow && mouseX >= listX + listWidth - 14;

            if (overRow) {
                gfx.fill(listX, rowY, listX + listWidth, rowY + ROW_HEIGHT, 0x22FFFFFF);
            }
            String text = patterns.get(index);
            String shown = font.width(text) > listWidth - 24
                    ? font.plainSubstrByWidth(text, listWidth - 30) + "..."
                    : text;
            gfx.text(font, shown, listX + 4, rowY + 3, GuiStyle.TEXT, false);
            gfx.text(font, "✘", listX + listWidth - 12, rowY + 3,
                    overRemove ? GuiStyle.ACCENT_RED : GuiStyle.TEXT_DIM, false);
        }
        gfx.disableScissor();

        if (patterns.size() > visibleRows) {
            int trackX = listX + listWidth - 3;
            gfx.fill(trackX, listY, trackX + 3, listY + listHeight, GuiStyle.SCROLLBAR_TRACK);
            int thumbHeight = Math.max(10, listHeight * visibleRows / patterns.size());
            int thumbY = listY + (listHeight - thumbHeight) * scrollRow
                    / Math.max(1, patterns.size() - visibleRows);
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
        int listHeight = visibleRows * ROW_HEIGHT;
        if (event.x() >= listX + listWidth - 14 && event.x() < listX + listWidth
                && event.y() >= listY && event.y() < listY + listHeight) {
            int row = (int) ((event.y() - listY) / ROW_HEIGHT);
            remove(scrollRow + row);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int maxScroll = Math.max(0, patterns().size() - visibleRows);
        if (maxScroll > 0) {
            scrollRow = Math.clamp(scrollRow - (int) Math.signum(vertical), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        config.save();
        minecraft.setScreenAndShow(parent);
    }
}
