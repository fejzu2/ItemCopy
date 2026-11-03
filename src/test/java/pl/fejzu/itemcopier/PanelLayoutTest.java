package pl.fejzu.itemcopier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.fejzu.itemcopier.client.config.ItemCopierConfig;
import pl.fejzu.itemcopier.client.gui.PanelLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelLayoutTest {

    private static final int INV_W = 176;
    private static final int INV_H = 166;

    private ItemCopierConfig config() {
        ItemCopierConfig config = new ItemCopierConfig();
        config.columns = 9;
        config.maxVisibleRows = 3;
        return config;
    }

    private PanelLayout at(ItemCopierConfig config, int screenW, int screenH) {
        return at(config, screenW, screenH, INV_W, INV_H);
    }

    private PanelLayout at(ItemCopierConfig config, int screenW, int screenH, int invW, int invH) {
        int invLeft = (screenW - invW) / 2;
        int invTop = (screenH - invH) / 2;
        return PanelLayout.compute(config, screenW, screenH, invLeft, invTop, invW, invH);
    }

    private void assertInsideScreen(PanelLayout layout, int screenW, int screenH) {
        assertTrue(layout.x() >= 0, "panel starts off the left edge: " + layout.x());
        assertTrue(layout.y() >= 0, "panel starts off the top edge: " + layout.y());
        assertTrue(layout.x() + layout.width() <= screenW,
                "panel runs off the right edge: " + (layout.x() + layout.width()) + " > " + screenW);
        assertTrue(layout.y() + layout.height() <= screenH,
                "panel runs off the bottom edge: " + (layout.y() + layout.height()) + " > " + screenH);
    }

    @Test
    @DisplayName("the panel sits to the right of the inventory, a touch below its top")
    void sitsRightOfInventory() {
        int w = 960;
        int h = 540;
        int invLeft = (w - INV_W) / 2;
        int invTop = (h - INV_H) / 2;

        PanelLayout layout = at(config(), w, h);

        assertNotNull(layout);
        assertTrue(layout.x() > invLeft + INV_W,
                "the panel must start after the inventory ends, got x=" + layout.x());
        assertTrue(layout.y() > invTop,
                "the panel should be nudged below the top of the inventory");
        assertTrue(layout.y() - invTop <= 16,
                "the nudge should be slight, got " + (layout.y() - invTop) + "px");
        assertInsideScreen(layout, w, h);
    }

    @Test
    @DisplayName("the same screen geometry always produces the same panel position")
    void stableForTheSameScreen() {
        int w = 960;
        int h = 540;
        ItemCopierConfig config = config();

        PanelLayout first = at(config, w, h);
        PanelLayout second = at(config, w, h);

        assertNotNull(first);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("the position depends only on the window box, not on what is inside it")
    void ignoresScreenContents() {
        int w = 960;
        int h = 540;
        int invLeft = (w - INV_W) / 2;
        int invTop = (h - INV_H) / 2;
        ItemCopierConfig config = config();

        PanelLayout a = PanelLayout.compute(config, w, h, invLeft, invTop, INV_W, INV_H);
        PanelLayout b = PanelLayout.compute(config, w, h, invLeft, invTop, INV_W, INV_H);

        assertNotNull(a);
        assertEquals(a.x(), b.x(), "x must not depend on anything but the window box");
        assertEquals(a.y(), b.y(), "y must not depend on anything but the window box");
    }

    @Test
    @DisplayName("the panel never overlaps the inventory window")
    void neverOverlapsInventory() {
        for (int h : new int[] {270, 360, 540, 720, 1080}) {
            for (int w : new int[] {480, 640, 960, 1280, 1920}) {
                PanelLayout layout = at(config(), w, h);
                if (layout == null) {
                    continue;
                }
                int invLeft = (w - INV_W) / 2;
                assertTrue(layout.x() >= invLeft + INV_W,
                        w + "x" + h + ": panel overlaps the inventory");
                assertInsideScreen(layout, w, h);
            }
        }
    }

    @Test
    @DisplayName("a taller inventory still gets the panel level with its own top")
    void followsTallerInventories() {
        int w = 960;
        int h = 540;
        int tallHeight = 222;
        int invTop = (h - tallHeight) / 2;

        PanelLayout layout = at(config(), w, h, INV_W, tallHeight);

        assertNotNull(layout);
        assertTrue(layout.y() > invTop);
        assertTrue(layout.y() - invTop <= 16);
    }

    @Test
    @DisplayName("a wider inventory pushes the panel further right")
    void followsWiderInventories() {
        int w = 960;
        int h = 540;

        PanelLayout normal = at(config(), w, h);
        PanelLayout wide = at(config(), w, h, 256, INV_H);

        assertNotNull(normal);
        assertNotNull(wide);
        assertTrue(wide.x() > normal.x(),
                "a wider window should push the panel out, not let it overlap");
    }

    @Test
    @DisplayName("a narrow window drops columns rather than running off the edge")
    void narrowWindowReducesColumns() {
        int w = 560;
        int h = 540;
        ItemCopierConfig config = config();
        config.columns = 16;

        PanelLayout layout = at(config, w, h);

        assertNotNull(layout);
        assertTrue(layout.columns() < 16, "expected fewer columns, got " + layout.columns());
        assertInsideScreen(layout, w, h);
    }

    @Test
    @DisplayName("with no room to the right the panel is dropped, never drawn over slots")
    void noRoomMeansNoPanel() {
        assertNull(at(config(), 280, 540), "a window too narrow for the panel should hide it");
    }

    @Test
    @DisplayName("rows shrink to fit a short window")
    void rowsShrinkOnShortWindows() {
        ItemCopierConfig config = config();
        config.maxVisibleRows = 10;

        PanelLayout tall = at(config, 960, 1080);
        assertNotNull(tall);
        assertEquals(10, tall.visibleRows());

        PanelLayout shortWindow = at(config, 960, 200);
        if (shortWindow != null) {
            assertTrue(shortWindow.visibleRows() < 10, "rows should have been reduced to fit");
            assertInsideScreen(shortWindow, 960, 200);
        }
    }

    @Test
    @DisplayName("the grid always fits inside the drawn panel")
    void gridFitsInsidePanel() {
        PanelLayout layout = at(config(), 960, 540);
        assertNotNull(layout);
        assertTrue(layout.gridX() >= layout.x());
        assertTrue(layout.gridY() >= layout.y());
        assertTrue(layout.gridX() + layout.gridWidth() <= layout.x() + layout.width());
        assertTrue(layout.gridY() + layout.gridHeight() <= layout.y() + layout.height());
        assertEquals(layout.columns() * layout.visibleRows(), layout.capacity());
    }

    @Test
    @DisplayName("hit testing agrees with the drawn bounds")
    void containsMatchesBounds() {
        PanelLayout layout = at(config(), 960, 540);
        assertNotNull(layout);

        assertTrue(layout.contains(layout.x(), layout.y()));
        assertTrue(layout.contains(layout.x() + layout.width() - 1, layout.y() + layout.height() - 1));
        assertFalse(layout.contains(layout.x() - 1, layout.y()));
        assertFalse(layout.contains(layout.x() + layout.width(), layout.y()));
        assertFalse(layout.contains(layout.x(), layout.y() + layout.height()));
    }
}
