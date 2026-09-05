package dev.nextkey;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Settings | Tools | Next Key. The table lists every shortcut in the active keymap that starts
 * with a modifier, and lets each one be shown or hidden, renamed, and re-categorized. Applying
 * rewrites next-key.conf in full, so hand-editing that file keeps working — both sides are the
 * same store.
 */
public final class HintConfigurable implements Configurable {

    private static final String[] COLUMN_KEYS = {
            "settings.column.visible", "settings.column.shortcut", "settings.column.action",
            "settings.column.label", "settings.column.category", "settings.column.id"};
    private static final int[] WIDTHS = {52, 150, 260, 150, 110, 210};

    private static final class Item {
        final String id;
        final String name;
        final List<String> shortcuts = new ArrayList<>();
        boolean checked;
        String label;
        String category;

        Item(String id, String name) {
            this.id = id;
            this.name = name;
        }

        String shortcutText() {
            return String.join(", ", shortcuts);
        }
    }

    private final class Model extends AbstractTableModel {
        private final List<Item> items;

        Model(List<Item> items) {
            this.items = items;
        }

        @Override
        public int getRowCount() {
            return items.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_KEYS.length;
        }

        @Override
        public String getColumnName(int column) {
            return NextKeyBundle.message(COLUMN_KEYS[column]);
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 || column == 3 || column == 4;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Item item = items.get(row);
            switch (column) {
                case 0:
                    return item.checked;
                case 1:
                    return item.shortcutText();
                case 2:
                    return item.name;
                case 3:
                    return item.label;
                case 4:
                    return Category.displayName(item.category);
                default:
                    return item.id;
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            Item item = items.get(row);
            switch (column) {
                case 0:
                    item.checked = Boolean.TRUE.equals(value);
                    break;
                case 3:
                    item.label = value == null ? "" : value.toString().trim();
                    break;
                case 4:
                    item.category = value == null || value.toString().trim().isEmpty()
                            ? Category.guess(item.id)
                            : Category.fromDisplayName(value.toString().trim());
                    break;
                default:
                    return;
            }
            dirty = true;
            fireTableRowsUpdated(row, row);
        }
    }

    private JPanel root;
    private JBTable table;
    private Model model;
    private TableRowSorter<Model> sorter;
    private JBCheckBox showAll;
    private JBCheckBox mergeNumbered;
    private JSpinner delay;
    private JSlider opacity;
    private JLabel opacityValue;
    private JTextField filter;
    private boolean dirty;

    @Override
    public String getDisplayName() {
        return "Next Key";
    }

    @Override
    public JComponent createComponent() {
        showAll = new JBCheckBox(NextKeyBundle.message("settings.showAll"));
        mergeNumbered = new JBCheckBox(NextKeyBundle.message("settings.mergeNumbered"));
        showAll.addActionListener(e -> dirty = true);
        mergeNumbered.addActionListener(e -> dirty = true);

        delay = new JSpinner(new SpinnerNumberModel(HintSettings.DEFAULT_DELAY_MS,
                HintSettings.MIN_DELAY_MS, HintSettings.MAX_DELAY_MS, 50));
        Dimension delaySize = new Dimension(80, delay.getPreferredSize().height);
        delay.setPreferredSize(delaySize);
        delay.setMaximumSize(delaySize);
        delay.addChangeListener(e -> dirty = true);

        opacity = new JSlider(20, 100, 100);
        opacity.setMajorTickSpacing(20);
        opacity.setPaintTicks(true);
        // BoxLayout stretches to maximumSize, so setting only the preferred size lets the
        // slider fill the row and push its own percentage label out of sight
        Dimension sliderSize = new Dimension(240, opacity.getPreferredSize().height);
        opacity.setPreferredSize(sliderSize);
        opacity.setMaximumSize(sliderSize);
        opacityValue = new JLabel("100%");
        opacityValue.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        opacity.addChangeListener(e -> {
            opacityValue.setText(opacity.getValue() + "%");
            if (!opacity.getValueIsAdjusting()) {
                dirty = true;
            }
        });

        filter = new JTextField();
        filter.setColumns(24);
        filter.setMaximumSize(new Dimension(320, filter.getPreferredSize().height));
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        model = new Model(new ArrayList<>());
        table = new JBTable(model);
        table.setStriped(true);
        table.setRowHeight(24);
        // No auto-resize: otherwise a narrow dialog squeezes every column and the Action column
        // is the first to turn into "Show Co...". Scroll horizontally instead.
        table.setAutoResizeMode(JBTable.AUTO_RESIZE_OFF);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < WIDTHS.length; i++) {
            columns.getColumn(i).setPreferredWidth(WIDTHS[i]);
            columns.getColumn(i).setMinWidth(Math.min(WIDTHS[i], 60));
        }
        JComboBox<String> categories = new JComboBox<>(Category.displayNames());
        categories.setEditable(true);
        columns.getColumn(4).setCellEditor(new DefaultCellEditor(categories));

        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        options.add(leftAligned(showAll));
        options.add(leftAligned(mergeNumbered));
        options.add(Box.createVerticalStrut(6));
        options.add(row(NextKeyBundle.message("settings.delay"), delay, new JLabel(" ms")));
        options.add(Box.createVerticalStrut(4));
        options.add(row(NextKeyBundle.message("settings.opacity"), opacity, opacityValue));
        options.add(Box.createVerticalStrut(6));
        options.add(row(NextKeyBundle.message("settings.filter"), filter, null));

        JBScrollPane scroll = new JBScrollPane(table);
        scroll.setPreferredSize(new Dimension(900, 420));

        // The note runs past one line; without the HTML wrapper a JLabel clips it instead
        // of wrapping
        JLabel footer = new JLabel("<html>" + NextKeyBundle.message("settings.footer") + "</html>");
        footer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        root = new JPanel(new BorderLayout());
        root.add(options, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        reset();
        return root;
    }

    private static JComponent row(String label, JComponent field, JComponent suffix) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(new JLabel(label));
        panel.add(Box.createHorizontalStrut(6));
        panel.add(field);
        if (suffix != null) {
            panel.add(suffix);
        }
        panel.add(Box.createHorizontalGlue());
        return panel;
    }

    private static JComponent leftAligned(JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(component, BorderLayout.WEST);
        return panel;
    }

    private void applyFilter() {
        String text = filter.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text), 1, 2, 3, 4, 5));
        }
    }

    @Override
    public boolean isModified() {
        return dirty;
    }

    @Override
    public void apply() {
        if (model == null) {
            return;
        }
        HintSettings settings = HintSettings.load();
        settings.setShowAll(showAll.isSelected());
        settings.setMergeNumbered(mergeNumbered.isSelected());
        settings.setDelayMs(((Number) delay.getValue()).intValue());
        settings.setOpacity(opacity.getValue() / 100f);
        for (Item item : model.items) {
            settings.setChecked(item.id, item.checked);
            settings.setLabel(item.id, item.label);
            settings.setCategory(item.id, item.category);
        }
        settings.save(ShortcutIndex.collect());
        ShortcutIndex.invalidate();
        dirty = false;
    }

    @Override
    public void reset() {
        if (model == null) {
            return;
        }
        HintSettings.invalidateStamp();
        HintSettings settings = HintSettings.load();
        showAll.setSelected(settings.isShowAll());
        mergeNumbered.setSelected(settings.isMergeNumbered());
        delay.setValue(settings.getDelayMs());
        opacity.setValue(Math.round(settings.getOpacity() * 100));
        opacityValue.setText(opacity.getValue() + "%");

        Map<String, Item> byId = new LinkedHashMap<>();
        for (ShortcutIndex.Raw raw : ShortcutIndex.collect()) {
            Item item = byId.get(raw.actionId);
            if (item == null) {
                item = new Item(raw.actionId, raw.name);
                item.checked = settings.isChecked(raw.actionId);
                item.label = settings.rawLabel(raw.actionId);
                item.category = settings.category(raw.actionId);
                byId.put(raw.actionId, item);
            }
            String text = raw.shortcutText();
            if (!item.shortcuts.contains(text)) {
                item.shortcuts.add(text);
            }
        }

        List<Item> items = new ArrayList<>(byId.values());
        items.sort(new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                int r = Category.order(a.category) - Category.order(b.category);
                if (r != 0) {
                    return r;
                }
                return String.CASE_INSENSITIVE_ORDER.compare(a.shortcutText(), b.shortcutText());
            }
        });

        model.items.clear();
        model.items.addAll(items);
        model.fireTableDataChanged();
        dirty = false;
    }

    @Override
    public void disposeUIResources() {
        root = null;
        table = null;
        model = null;
        sorter = null;
        showAll = null;
        mergeNumbered = null;
        delay = null;
        opacity = null;
        opacityValue = null;
        filter = null;
    }
}
