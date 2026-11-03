package pl.fejzu.itemcopier.client.gui;

import pl.fejzu.itemcopier.client.config.ItemCopierConfig;

public record PanelLayout(
        int x,
        int y,
        int width,
        int height,
        int columns,
        int visibleRows
) {
    private static final int SCREEN_MARGIN = 2;
    private static final int TOP_DROP = 10;

    public static PanelLayout compute(
            ItemCopierConfig config,
            int screenWidth,
            int screenHeight,
            int invLeft,
            int invTop,
            int invWidth,
            int invHeight
    ) {
        int columns = Math.clamp(config.columns, ItemCopierConfig.MIN_COLUMNS, ItemCopierConfig.MAX_COLUMNS);
        int maxRows = Math.clamp(config.maxVisibleRows, ItemCopierConfig.MIN_ROWS, ItemCopierConfig.MAX_ROWS);

        int anchorX = invLeft + invWidth + GuiStyle.GAP;
        int room = screenWidth - SCREEN_MARGIN - anchorX;

        while (columns > ItemCopierConfig.MIN_COLUMNS && panelWidth(columns) > room) {
            columns--;
        }
        int width = panelWidth(columns);
        if (width > room) {
            return null;
        }

        int rows = rowsThatFit(screenHeight - 2 * SCREEN_MARGIN, maxRows);
        if (rows < 1) {
            return null;
        }
        int height = panelHeight(rows);

        int y = invTop + TOP_DROP;
        y = Math.clamp(y, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenHeight - height - SCREEN_MARGIN));

        return new PanelLayout(anchorX, y, width, height, columns, rows);
    }

    private static int rowsThatFit(int availableHeight, int maxRows) {
        int rows = (availableHeight - chromeHeight()) / GuiStyle.SLOT_SIZE;
        return Math.min(rows, maxRows);
    }

    public static int panelWidth(int columns) {
        return columns * GuiStyle.SLOT_SIZE
                + 2 * GuiStyle.PADDING
                + GuiStyle.SCROLLBAR_WIDTH
                + 2;
    }

    public static int panelHeight(int rows) {
        return chromeHeight() + rows * GuiStyle.SLOT_SIZE;
    }

    private static int chromeHeight() {
        return GuiStyle.PADDING
                + GuiStyle.HEADER_HEIGHT
                + GuiStyle.SEARCH_HEIGHT
                + 2
                + GuiStyle.FOOTER_HEIGHT
                + GuiStyle.PADDING;
    }

    public int gridX() {
        return x + GuiStyle.PADDING;
    }

    public int gridY() {
        return y + GuiStyle.PADDING + GuiStyle.HEADER_HEIGHT + GuiStyle.SEARCH_HEIGHT + 2;
    }

    public int gridWidth() {
        return columns * GuiStyle.SLOT_SIZE;
    }

    public int gridHeight() {
        return visibleRows * GuiStyle.SLOT_SIZE;
    }

    public int capacity() {
        return columns * visibleRows;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
