package pl.fejzu.itemcopier.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ItemCopierSettingsScreen extends Screen {
    private final Screen parent;
    private final ItemCopierConfig config;

    private final List<Row> rows = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    private record Row(String label, Supplier<String> value, int y) {
    }

    public ItemCopierSettingsScreen(Screen parent, ItemCopierConfig config) {
        super(Component.translatable("itemcopier.settings.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        rows.clear();
        panelWidth = Math.min(320, width - 20);
        panelHeight = Math.min(252, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int labelX = panelX + 12;
        int buttonWidth = 96;
        int buttonX = panelX + panelWidth - 12 - buttonWidth;
        int y = panelY + 28;
        int step = 19;

        y = addRow(labelX, buttonX, buttonWidth, y, step,
                "Panel", () -> config.panelEnabled ? "Shown" : "Hidden",
                "Draw the library under the inventory.",
                () -> config.panelEnabled = !config.panelEnabled);

        y = addRow(labelX, buttonX, buttonWidth, y, step,
                "Panel scale", () -> String.format("%.2fx", config.panelScale),
                "Extra scale on top of the game GUI scale.",
                () -> {
                    float next = Math.round((config.panelScale + 0.25f) * 100f) / 100f;
                    config.panelScale = next > 1.5f ? 0.5f : next;
                });

        y = addRow(labelX, buttonX, buttonWidth, y, step,
                "Columns", () -> String.valueOf(config.columns),
                "Items per row in the panel grid.",
                () -> config.columns = config.columns >= ItemCopierConfig.MAX_COLUMNS
                        ? ItemCopierConfig.MIN_COLUMNS : config.columns + 1);

        y = addRow(labelX, buttonX, buttonWidth, y, step,
                "Max rows", () -> String.valueOf(config.maxVisibleRows),
                "Upper bound; fewer are shown when space runs out.",
                () -> config.maxVisibleRows = config.maxVisibleRows >= ItemCopierConfig.MAX_ROWS
                        ? ItemCopierConfig.MIN_ROWS : config.maxVisibleRows + 1);

        y = addRow(labelX, buttonX, buttonWidth, y, step,
                "Notifications", () -> config.copyNotifications ? "On" : "Off",
                "Chat confirmation after copying or inserting.",
                () -> config.copyNotifications = !config.copyNotifications);

        y = addRow(labelX, buttonX, buttonWidth, y, step,
                "Duplicates", () -> config.duplicates().id,
                "What to do when the same item is copied twice.",
                () -> config.setDuplicates(config.duplicates().next()));

        y = addRow(labelX, buttonX, buttonWidth, y, step,
                "Click action", () -> config.insertion().id,
                "What a left click on a saved item does.",
                () -> config.setInsertion(config.insertion().next()));

        y = addRow(labelX, buttonX, buttonWidth, y, step,
                "Source server", () -> config.recordSourceServer ? "Recorded" : "Not recorded",
                "Store which server an item was copied from.",
                () -> config.recordSourceServer = !config.recordSourceServer);

        y = addActionRow(labelX, buttonX, buttonWidth, y, step,
                "Lore filters", this::loreFilterSummary,
                "Wzorce tekstu oraz ucinanie ostatnich linijek lore przy kopiowaniu.",
                () -> minecraft.setScreenAndShow(new LorePatternsScreen(this, config)));

        y = addRow(labelX, buttonX, buttonWidth, y, step,
                "Debug logging", () -> config.debugLogging ? "On" : "Off",
                "Write extra detail to the game log.",
                () -> config.debugLogging = !config.debugLogging);

        addRenderableWidget(Button.builder(Component.literal("Key bindings"),
                        b -> minecraft.setScreenAndShow(new KeyBindsScreen(this, minecraft.options)))
                .bounds(panelX + 12, panelY + panelHeight - 22, (panelWidth - 28) / 2, 16)
                .tooltip(Tooltip.create(Component.literal(
                        "Rebind the copy key and the library key under ItemCopier.")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(panelX + panelWidth / 2 + 2, panelY + panelHeight - 22,
                        (panelWidth - 28) / 2, 16)
                .build());
    }

    private String loreFilterSummary() {
        int patterns = config.lorePatterns().size();
        if (config.stripLastLoreLines > 0) {
            return patterns + " + " + config.stripLastLoreLines + " ost.";
        }
        return patterns + " wzorcow";
    }

    private int addRow(
            int labelX,
            int buttonX,
            int buttonWidth,
            int y,
            int step,
            String label,
            Supplier<String> value,
            String help,
            Runnable onClick
    ) {
        Button button = Button.builder(Component.literal(value.get()), b -> {
            onClick.run();
            config.save();
            b.setMessage(Component.literal(value.get()));
        }).bounds(buttonX, y, buttonWidth, 16).tooltip(Tooltip.create(Component.literal(help))).build();
        addRenderableWidget(button);
        rows.add(new Row(label, value, y));
        return y + step;
    }

    private int addActionRow(
            int labelX,
            int buttonX,
            int buttonWidth,
            int y,
            int step,
            String label,
            Supplier<String> value,
            String help,
            Runnable onClick
    ) {
        addRenderableWidget(Button.builder(Component.literal(value.get()), b -> onClick.run())
                .bounds(buttonX, y, buttonWidth, 16)
                .tooltip(Tooltip.create(Component.literal(help)))
                .build());
        rows.add(new Row(label, value, y));
        return y + step;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float tickDelta) {
        GuiStyle.panel(gfx, panelX, panelY, panelWidth, panelHeight);
        gfx.text(font, Component.translatable("itemcopier.settings.title"),
                panelX + 12, panelY + 10, GuiStyle.ACCENT_GOLD, false);
        GuiStyle.divider(gfx, panelX + 12, panelY + 22, panelWidth - 24);

        for (Row row : rows) {
            gfx.text(font, row.label(), panelX + 12, row.y() + 4, GuiStyle.TEXT, false);
        }
        GuiStyle.divider(gfx, panelX + 12, panelY + panelHeight - 26, panelWidth - 24);

        super.extractRenderState(gfx, mouseX, mouseY, tickDelta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float tickDelta) {
        gfx.fill(0, 0, width, height, 0xC0000000);
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
