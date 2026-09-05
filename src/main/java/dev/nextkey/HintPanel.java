package dev.nextkey;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * The hint panel: one title line naming the prefix currently held, then the candidate keys laid
 * out in sections by category, wrapping into more columns when they do not fit.
 * <p>
 * Columns are split and everything is painted by hand. There are often a couple of hundred
 * candidates, and that many JLabels costs real layout time, while all this needs is two columns
 * of aligned text.
 */
final class HintPanel extends JPanel {

    private static final int PAD = 14;          // panel padding
    private static final int COL_GAP = 22;      // gap between columns
    private static final int KEY_GAP = 12;      // gap between the key and the action name
    private static final int TITLE_GAP = 10;    // gap under the title line
    private static final int SECTION_TOP = 7;   // space above a category heading
    private static final int KEY_PAD = 7;       // keycap horizontal padding
    private static final int KEY_RADIUS = 4;    // keycap corner radius
    private static final int CAP_INSET = 2;     // keycap inset within the row height

    /** Popup corner radius; {@link HintWindow} clips the window to the same value. */
    static final int CORNER_RADIUS = 10;

    /** Action names wider than this are truncated, so one long name cannot widen a column. */
    private static final int MAX_NAME_WIDTH = 240;

    private static final class Row {
        final String left;
        final String right;
        final boolean header;

        Row(String left, String right, boolean header) {
            this.left = left;
            this.right = right;
            this.header = header;
        }
    }

    private static final class Column {
        final List<Row> rows = new ArrayList<>();
        int keyWidth;
        int width;
    }

    private final Font keyFont;
    private final Font textFont;
    private final Font titleFont;
    private final Font sectionFont;
    private final Color keyColor;
    private final Color capFill;
    private final Color capLine;
    private final Color textColor;
    private final Color titleColor;
    private final Color sectionColor;
    private final Color subtitleColor;
    private final Color ruleColor;
    private final Color borderColor;
    private final int rowHeight;
    private final int titleHeight;
    private final int sectionHeight;

    private final List<Column> columns = new ArrayList<>();
    private String title = "";
    private String subtitle = "";
    private Dimension preferred = new Dimension(0, 0);

    HintPanel() {
        Color background = UIManager.getColor("Panel.background");
        if (background == null) {
            background = new Color(0x2B2D30);
        }
        boolean dark = luminance(background) < 128;

        keyColor = dark ? new Color(0xD0E2F5) : new Color(0x1C548C);
        capFill = dark ? new Color(0x61, 0xAF, 0xEF, 38) : new Color(0x20, 0x60, 0xA0, 26);
        capLine = dark ? new Color(0x61, 0xAF, 0xEF, 120) : new Color(0x20, 0x60, 0xA0, 110);
        textColor = dark ? new Color(0xABB2BF) : new Color(0x42484F);
        titleColor = dark ? new Color(0xD6DCE6) : new Color(0x2E343C);
        sectionColor = dark ? new Color(0xE5C07B) : new Color(0x966C1E);
        subtitleColor = dark ? new Color(0x808896) : new Color(0x8A8F96);
        ruleColor = dark ? new Color(0x4A4F58) : new Color(0xC8CCD2);
        borderColor = dark ? new Color(0x5A606B) : new Color(0xB4BAC2);

        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.DIALOG, Font.PLAIN, 12);
        }
        textFont = base.deriveFont(Font.PLAIN, 11f);
        titleFont = base.deriveFont(Font.BOLD, 12f);
        sectionFont = base.deriveFont(Font.BOLD, 11f);
        keyFont = new Font("Consolas", Font.BOLD, 11);

        FontMetrics textMetrics = getFontMetrics(textFont);
        FontMetrics keyMetrics = getFontMetrics(keyFont);
        rowHeight = Math.max(textMetrics.getHeight(), keyMetrics.getHeight()) + 6;
        titleHeight = getFontMetrics(titleFont).getHeight() + TITLE_GAP;
        sectionHeight = getFontMetrics(sectionFont).getHeight() + SECTION_TOP + 3;

        setOpaque(true);
        setBackground(background);
    }

    /** Replaces the content and re-splits the columns. Returns the new preferred size. */
    Dimension setContent(String newTitle, String newSubtitle, List<ShortcutIndex.Group> groups) {
        title = newTitle == null ? "" : newTitle;
        subtitle = newSubtitle == null ? "" : newSubtitle;
        columns.clear();

        FontMetrics textMetrics = getFontMetrics(textFont);
        FontMetrics keyMetrics = getFontMetrics(keyFont);
        FontMetrics titleMetrics = getFontMetrics(titleFont);
        FontMetrics sectionMetrics = getFontMetrics(sectionFont);

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int maxHeight = (int) (screen.height * 0.84) - PAD * 2 - titleHeight;
        int maxWidth = (int) (screen.width * 0.92) - PAD * 2;

        // Work out the minimum column count from the screen height, then split by average
        // height, so the last column does not end up holding one or two entries
        int contentHeight = 0;
        for (ShortcutIndex.Group group : groups) {
            if (!group.entries.isEmpty()) {
                contentHeight += sectionHeight + group.entries.size() * rowHeight;
            }
        }
        int columnCount = Math.max(1, (contentHeight + maxHeight - 1) / maxHeight);
        int target = Math.max(rowHeight * 4, (contentHeight + columnCount - 1) / columnCount);
        List<Column> split = split(groups, Math.min(maxHeight, target), textMetrics);
        if (split.size() > columnCount) {
            // Category headings pushed the count past the target; fall back to filling
            // columns to the screen height
            split = split(groups, maxHeight, textMetrics);
        }
        columns.addAll(split);

        for (Column column : columns) {
            int keyWidth = 0;
            for (Row row : column.rows) {
                if (!row.header) {
                    keyWidth = Math.max(keyWidth, capWidth(row.left, keyMetrics));
                }
            }
            int width = 0;
            for (Row row : column.rows) {
                if (row.header) {
                    width = Math.max(width, sectionMetrics.stringWidth(row.left) + 34);
                } else {
                    width = Math.max(width, keyWidth + KEY_GAP + textMetrics.stringWidth(row.right));
                }
            }
            column.keyWidth = keyWidth;
            column.width = width;
        }

        // Too wide for the screen: drop whole columns off the end and say so in the subtitle
        int total = PAD * 2;
        int keep = 0;
        for (Column column : columns) {
            int next = total + column.width + (keep > 0 ? COL_GAP : 0);
            if (keep > 0 && next > maxWidth) {
                break;
            }
            total = next;
            keep++;
        }
        if (keep < columns.size()) {
            int dropped = 0;
            for (int i = keep; i < columns.size(); i++) {
                for (Row row : columns.get(i).rows) {
                    if (!row.header) {
                        dropped++;
                    }
                }
            }
            while (columns.size() > keep) {
                columns.remove(columns.size() - 1);
            }
            subtitle = NextKeyBundle.message("hint.subtitle.dropped", subtitle, dropped);
        }

        int height = 0;
        for (Column column : columns) {
            int columnHeight = 0;
            for (Row row : column.rows) {
                columnHeight += row.header ? sectionHeight : rowHeight;
            }
            height = Math.max(height, columnHeight);
        }
        int titleWidth = titleMetrics.stringWidth(title) + 12
                + titleMetrics.stringWidth(subtitle) + PAD * 2;
        preferred = new Dimension(Math.max(total, titleWidth), titleHeight + height + PAD * 2);
        return new Dimension(preferred);
    }

    private static int capWidth(String key, FontMetrics keyMetrics) {
        return keyMetrics.stringWidth(key) + KEY_PAD * 2;
    }

    /** Truncates with an ellipsis once the text is wider than the limit. */
    private static String truncate(String text, FontMetrics metrics, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        int ellipsis = metrics.stringWidth("…");
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end)) + ellipsis > maxWidth) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    /** Splits the categories into columns under the given height limit; a heading never
     * lands at the bottom of a column. */
    private List<Column> split(List<ShortcutIndex.Group> groups, int limit, FontMetrics textMetrics) {
        List<Column> result = new ArrayList<>();
        Column current = new Column();
        int used = 0;
        for (ShortcutIndex.Group group : groups) {
            if (group.entries.isEmpty()) {
                continue;
            }
            // A heading is only worth starting near the bottom if two rows fit under it
            if (used > 0 && used + sectionHeight + rowHeight * 2 > limit) {
                result.add(current);
                current = new Column();
                used = 0;
            }
            current.rows.add(new Row(group.title, String.valueOf(group.entries.size()), true));
            used += sectionHeight;
            for (ShortcutIndex.Entry entry : group.entries) {
                if (used + rowHeight > limit) {
                    result.add(current);
                    current = new Column();
                    used = 0;
                    current.rows.add(new Row(
                            NextKeyBundle.message("hint.section.continued", group.title), "", true));
                    used += sectionHeight;
                }
                current.rows.add(new Row(entry.key,
                        truncate(entry.name, textMetrics, MAX_NAME_WIDTH), false));
                used += rowHeight;
            }
        }
        if (!current.rows.isEmpty()) {
            result.add(current);
        }
        return result;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(preferred);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                    CORNER_RADIUS * 2, CORNER_RADIUS * 2);

            FontMetrics titleMetrics = g2.getFontMetrics(titleFont);
            int baseline = PAD + titleMetrics.getAscent();
            g2.setFont(titleFont);
            g2.setColor(titleColor);
            g2.drawString(title, PAD, baseline);
            int titleWidth = titleMetrics.stringWidth(title);
            if (!subtitle.isEmpty()) {
                g2.setColor(subtitleColor);
                g2.drawString(subtitle, PAD + titleWidth + 12, baseline);
            }
            int ruleY = PAD + titleMetrics.getHeight() + 3;
            g2.setColor(ruleColor);
            g2.drawLine(PAD, ruleY, getWidth() - PAD, ruleY);

            FontMetrics textMetrics = g2.getFontMetrics(textFont);
            FontMetrics keyMetrics = g2.getFontMetrics(keyFont);
            FontMetrics sectionMetrics = g2.getFontMetrics(sectionFont);
            int x = PAD;
            for (Column column : columns) {
                int y = PAD + titleHeight;
                for (Row row : column.rows) {
                    if (row.header) {
                        int rowBaseline = y + SECTION_TOP + sectionMetrics.getAscent();
                        g2.setFont(sectionFont);
                        g2.setColor(sectionColor);
                        g2.drawString(row.left, x, rowBaseline);
                        int used = sectionMetrics.stringWidth(row.left);
                        if (!row.right.isEmpty()) {
                            g2.setColor(subtitleColor);
                            g2.drawString(row.right, x + used + 7, rowBaseline);
                            used += 7 + sectionMetrics.stringWidth(row.right);
                        }
                        if (used + 10 < column.width) {
                            g2.setColor(ruleColor);
                            int lineY = rowBaseline - sectionMetrics.getAscent() / 2 + 1;
                            g2.drawLine(x + used + 10, lineY, x + column.width, lineY);
                        }
                        y += sectionHeight;
                    } else {
                        int capTop = y + CAP_INSET;
                        int capHeight = rowHeight - CAP_INSET * 2;
                        int capWidth = capWidth(row.left, keyMetrics);
                        g2.setColor(capFill);
                        g2.fillRoundRect(x, capTop, capWidth, capHeight, KEY_RADIUS * 2, KEY_RADIUS * 2);
                        g2.setColor(capLine);
                        g2.drawRoundRect(x, capTop, capWidth, capHeight, KEY_RADIUS * 2, KEY_RADIUS * 2);

                        int rowBaseline = capTop
                                + (capHeight + keyMetrics.getAscent() - keyMetrics.getDescent()) / 2;
                        g2.setFont(keyFont);
                        g2.setColor(keyColor);
                        g2.drawString(row.left, x + KEY_PAD, rowBaseline);
                        g2.setFont(textFont);
                        g2.setColor(textColor);
                        g2.drawString(row.right, x + column.keyWidth + KEY_GAP, rowBaseline);
                        y += rowHeight;
                    }
                }
                x += column.width + COL_GAP;
            }
        } finally {
            g2.dispose();
        }
    }

    private static double luminance(Color c) {
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
    }
}
