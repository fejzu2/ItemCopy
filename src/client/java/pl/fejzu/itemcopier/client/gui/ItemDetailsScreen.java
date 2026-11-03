package pl.fejzu.itemcopier.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import pl.fejzu.itemcopier.client.creative.CreativeItemRestorer;
import pl.fejzu.itemcopier.client.item.CopiedItem;
import pl.fejzu.itemcopier.client.item.ItemCopyHandler;
import pl.fejzu.itemcopier.client.item.LoreEditor;
import pl.fejzu.itemcopier.client.serialization.GiveCommandBuilder;
import pl.fejzu.itemcopier.client.serialization.ItemStackSerializer;
import pl.fejzu.itemcopier.client.storage.ItemLibrary;
import pl.fejzu.itemcopier.client.storage.LibraryTransfer;
import pl.fejzu.itemcopier.client.util.ItemCopierLog;
import pl.fejzu.itemcopier.client.util.Messages;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemDetailsScreen extends Screen {
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final ItemLibrary library;
    private final CopiedItem entry;

    private EditBox labelField;
    private Button insertButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    private List<Line> lines = List.of();
    private boolean deleted;

    private record Line(String label, String value, int color) {
    }

    public ItemDetailsScreen(Screen parent, ItemLibrary library, CopiedItem entry) {
        super(Component.translatable("itemcopier.details.title"));
        this.parent = parent;
        this.library = library;
        this.entry = entry;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(300, width - 20);
        panelHeight = Math.min(224, height - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        lines = buildLines();

        int contentX = panelX + 10;
        int contentWidth = panelWidth - 20;

        labelField = new EditBox(font, contentX, panelY + panelHeight - 72, contentWidth, 16,
                Component.literal("Label"));
        labelField.setMaxLength(64);
        labelField.setValue(entry.label() != null ? entry.label() : "");
        labelField.setHint(Component.literal("Custom label (optional)"));
        addRenderableWidget(labelField);

        int loreY = panelY + panelHeight - 50;
        int loreLineCount = LoreEditor.linesOf(entry.stack(ItemCopyHandler.registries(minecraft))).size();
        Button loreButton = Button.builder(
                        Component.literal("Edit lore (" + loreLineCount + " lines)"), b -> editLore())
                .bounds(contentX, loreY, contentWidth, 14).build();
        loreButton.active = loreLineCount > 0 && !entry.isBroken();
        addRenderableWidget(loreButton);

        int buttonY = panelY + panelHeight - 32;
        int buttonWidth = (contentWidth - 2 * 4) / 3;

        addRenderableWidget(Button.builder(Component.literal("Raw data"), b -> copyRawData())
                .bounds(contentX, buttonY, buttonWidth, 14).build());
        addRenderableWidget(Button.builder(Component.literal("/give"), b -> copyGiveCommand())
                .bounds(contentX + buttonWidth + 4, buttonY, buttonWidth, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Export .nbt"), b -> exportEntry())
                .bounds(contentX + 2 * (buttonWidth + 4), buttonY, contentWidth - 2 * (buttonWidth + 4),
                        14).build());

        int bottomY = panelY + panelHeight - 16;
        insertButton = Button.builder(Component.literal("Give to me"), b -> insert())
                .bounds(contentX, bottomY, buttonWidth, 14).build();
        insertButton.active = CreativeItemRestorer.canRestore() && !entry.isBroken();
        addRenderableWidget(insertButton);

        addRenderableWidget(Button.builder(Component.literal("Save label"), b -> saveLabel())
                .bounds(contentX + buttonWidth + 4, bottomY, buttonWidth, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), b -> delete())
                .bounds(contentX + 2 * (buttonWidth + 4), bottomY,
                        contentWidth - 2 * (buttonWidth + 4), 14).build());
    }

    private List<Line> buildLines() {
        RegistryAccess registries = ItemCopyHandler.registries(minecraft);
        ItemStack stack = entry.stack(registries);
        List<Line> result = new ArrayList<>();

        result.add(new Line("Registry ID", entry.itemId(), GuiStyle.TEXT));
        result.add(new Line("Stack size", String.valueOf(entry.count()), GuiStyle.TEXT));

        int components = stack.isEmpty()
                ? entry.stackTag().getCompoundOrEmpty("components").size()
                : stack.getComponents().size();
        result.add(new Line("Components", String.valueOf(components), GuiStyle.TEXT));

        String model = entry.stackTag().getCompoundOrEmpty("components")
                .getCompound("minecraft:custom_model_data")
                .map(ItemStackSerializer::toSnbt)
                .orElse(null);
        if (model == null) {
            model = entry.stackTag().getCompoundOrEmpty("components")
                    .getString("minecraft:item_model")
                    .orElse("-");
        }
        result.add(new Line("Model", model, GuiStyle.TEXT_MUTED));

        result.add(new Line("Source", entry.sourceServer() != null ? entry.sourceServer() : "not recorded",
                GuiStyle.TEXT_MUTED));
        result.add(new Line("Copied", STAMP.format(entry.copiedAt()), GuiStyle.TEXT_MUTED));
        result.add(new Line("Game version",
                (entry.minecraftVersion() != null ? entry.minecraftVersion() : "?")
                        + " (data " + entry.dataVersion() + ")",
                GuiStyle.TEXT_MUTED));
        result.add(new Line("Local ID", entry.id(), GuiStyle.TEXT_DIM));

        if (entry.isBroken()) {
            result.add(new Line("Status", "cannot be read here", GuiStyle.ACCENT_RED));
            result.add(new Line("Reason", String.valueOf(entry.decodeError()), GuiStyle.ACCENT_RED));
        }
        return result;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float tickDelta) {
        GuiStyle.panel(gfx, panelX, panelY, panelWidth, panelHeight);

        int textX = panelX + 10;
        int y = panelY + 8;

        RegistryAccess registries = ItemCopyHandler.registries(minecraft);
        ItemStack stack = entry.stack(registries);
        if (!stack.isEmpty()) {
            gfx.item(stack, textX, y);
            gfx.itemDecorations(font, stack, textX, y);
        } else {
            GuiStyle.slot(gfx, textX, y);
            gfx.text(font, "?", textX + 6, y + 5, GuiStyle.ACCENT_RED, false);
        }

        gfx.text(font, entry.displayName(), textX + 22, y + 1, GuiStyle.ACCENT_GOLD, false);
        gfx.text(font, entry.itemId(), textX + 22, y + 11, GuiStyle.TEXT_DIM, false);

        y += 26;
        GuiStyle.divider(gfx, textX, y, panelWidth - 20);
        y += 4;

        int valueX = textX + 78;
        for (Line line : lines) {
            if (y + font.lineHeight > panelY + panelHeight - 80) {
                break;
            }
            gfx.text(font, line.label(), textX, y, GuiStyle.TEXT_MUTED, false);
            String value = line.value();
            if (font.width(value) > panelWidth - 20 - 78) {
                value = font.plainSubstrByWidth(value, panelWidth - 20 - 78 - font.width("...")) + "...";
            }
            gfx.text(font, value, valueX, y, line.color(), false);
            y += font.lineHeight + 1;
        }

        if (!insertButton.active && !entry.isBroken()) {
            gfx.text(font, "Creative Mode or appropriate permissions required",
                    textX, panelY + panelHeight - 84, GuiStyle.TEXT_DIM, false);
        }

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
        if (!deleted) {
            saveLabel();
        }
        library.flush();
        minecraft.setScreenAndShow(parent);
    }

    private void saveLabel() {
        String value = labelField.getValue().trim();
        String current = entry.label() != null ? entry.label() : "";
        if (value.equals(current)) {
            return;
        }
        library.rename(entry, value);
        library.flush();
        Messages.send("renamed", Map.of("label", entry.plainDisplayName()));
    }

    private void delete() {
        library.remove(entry);
        library.flush();
        deleted = true;
        Messages.send("deleted", Map.of("item", entry.plainDisplayName()));
        onClose();
    }

    private void insert() {
        CreativeItemRestorer.Result result = CreativeItemRestorer.insert(entry);
        if (result.success()) {
            Messages.send("restore-ok", Map.of("item", entry.plainDisplayName()));
        } else {
            Messages.send(result.messageKey(), Map.of(
                    "error", result.detail() != null ? result.detail() : "-",
                    "item", entry.itemId()));
        }
    }

    private void copyRawData() {
        Minecraft.getInstance().keyboardHandler.setClipboard(
                ItemStackSerializer.toPrettySnbt(entry.stackTag()));
        Messages.send("clipboard-snbt");
    }

    private void copyGiveCommand() {
        GiveCommandBuilder.Result result = GiveCommandBuilder.build(entry.stackTag(), "@s");
        Minecraft.getInstance().keyboardHandler.setClipboard(result.command());

        if (result.isComplete()) {
            Messages.send("clipboard-give");
            return;
        }
        if (!result.skipped().isEmpty()) {
            Messages.send("clipboard-give-partial", Map.of(
                    "count", String.valueOf(result.skipped().size()),
                    "components", String.join(", ", result.skipped())));
        }
        if (!result.fitsInChat()) {
            Messages.send("clipboard-give-too-long", Map.of(
                    "length", String.valueOf(result.length()),
                    "limit", String.valueOf(GiveCommandBuilder.CHAT_INPUT_LIMIT)));
        }
    }

    private void editLore() {
        minecraft.setScreenAndShow(new LoreEditorScreen(parent, library, entry));
    }

    private void exportEntry() {
        try {
            Path file = LibraryTransfer.exportSingle(entry);
            Messages.send("export-ok", Map.of("file", file.toString()));
        } catch (IOException | RuntimeException e) {
            ItemCopierLog.error("Export failed", e);
            Messages.send("export-failed", Map.of("error",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }
}
