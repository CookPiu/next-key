package dev.nextkey;

import javax.swing.JWindow;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * The focusless window that carries {@link HintPanel}, pinned to the bottom right of the active
 * IDE window. A JWindow rather than a JBPopup because the content changes as soon as the modifier
 * combination does: a JBPopup can only be cancelled and rebuilt, which flickers on every swap,
 * while a JWindow can be resized and repainted in place.
 */
final class HintWindow {

    private static final int MARGIN_RIGHT = 40;
    private static final int MARGIN_BOTTOM = 60;

    private final HintPanel panel = new HintPanel();
    private JWindow window;
    private Window owner;

    void show(Window newOwner, String title, String subtitle, List<ShortcutIndex.Group> groups,
              float opacity) {
        if (newOwner == null || !newOwner.isShowing() || groups == null || groups.isEmpty()) {
            hide();
            return;
        }
        if (window == null || owner != newOwner) {
            dispose();
            owner = newOwner;
            window = new JWindow(newOwner);
            window.setFocusableWindowState(false);
            window.setAutoRequestFocus(false);
            window.setContentPane(panel);
        }
        applyOpacity(opacity);

        Dimension size = panel.setContent(title, subtitle, groups);
        if (size.width <= 0 || size.height <= 0) {
            hide();
            return;
        }

        Rectangle bounds = newOwner.getBounds();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = bounds.x + bounds.width - size.width - MARGIN_RIGHT;
        int y = bounds.y + bounds.height - size.height - MARGIN_BOTTOM;
        x = Math.max(screen.x + 8, Math.min(x, screen.x + screen.width - size.width - 8));
        y = Math.max(screen.y + 8, Math.min(y, screen.y + screen.height - size.height - 8));

        window.setBounds(x, y, size.width, size.height);
        applyShape(size);
        if (window.isVisible()) {
            panel.repaint();
        } else {
            window.setVisible(true);
        }
    }

    /**
     * Clips the window to rounded corners so it lines up with the rounded border
     * {@link HintPanel} draws; without the clip a square patch of background shows outside each
     * corner. Has to be redone whenever the size changes.
     */
    private void applyShape(Dimension size) {
        try {
            GraphicsDevice device = device();
            if (device != null && device.isWindowTranslucencySupported(
                    GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {
                window.setShape(new RoundRectangle2D.Float(0, 0, size.width, size.height,
                        HintPanel.CORNER_RADIUS * 2f, HintPanel.CORNER_RADIUS * 2f));
            }
        } catch (Throwable ignored) {
            // Square corners where per-pixel transparency is unavailable
        }
    }

    private GraphicsDevice device() {
        if (window.getGraphicsConfiguration() != null) {
            return window.getGraphicsConfiguration().getDevice();
        }
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    }

    /**
     * Whole-window opacity. Not every environment supports it — remote desktop sessions and some
     * Linux compositors turn it off — and staying opaque is a better outcome there than throwing
     * or not showing at all.
     */
    private void applyOpacity(float opacity) {
        float value = Math.max(0.2f, Math.min(1.0f, opacity));
        try {
            GraphicsDevice device = device();
            if (value >= 1.0f
                    || device.isWindowTranslucencySupported(
                            GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
                window.setOpacity(value);
            }
        } catch (Throwable ignored) {
            // Stay fully opaque
        }
    }

    boolean isShowing() {
        return window != null && window.isVisible();
    }

    void hide() {
        if (window != null && window.isVisible()) {
            window.setVisible(false);
        }
    }

    void dispose() {
        if (window != null) {
            window.dispose();
            window = null;
            owner = null;
        }
    }
}
