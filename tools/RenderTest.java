package dev.nextkey;

import javax.imageio.ImageIO;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Renders {@link HintPanel} offline from a TSV exported out of a real keymap, to check
 * categories, folding, column splitting and truncation. No IDE is loaded, only Swing painting:
 *   java -cp build\classes;build\test-classes;IDE\lib\* dev.nextkey.RenderTest tsv out.png dark 0
 * Each TSV line holds bucket(0-3), key and action-id separated by tabs, where the bucket means
 * Ctrl / Ctrl+Shift / Ctrl+Alt / Ctrl+Alt+Shift. Categories come from {@link Category#guess};
 * the action id stands in for the display name, which only exists inside the IDE.
 */
public final class RenderTest {

    private static final String[] MODIFIERS = {
            "Ctrl", "Ctrl + Shift", "Ctrl + Alt", "Ctrl + Alt + Shift"
    };

    public static void main(String[] args) throws Exception {
        String tsv = args[0];
        String out = args[1];
        boolean dark = args.length < 3 || "dark".equals(args[2]);
        int bucket = args.length < 4 ? 0 : Integer.parseInt(args[3]);

        UIManager.put("Panel.background", dark ? new Color(0x2B2D30) : new Color(0xF2F2F2));

        Map<String, List<ShortcutIndex.Entry>> byCategory = new TreeMap<>(new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int r = Category.order(a) - Category.order(b);
                return r != 0 ? r : a.compareTo(b);
            }
        });
        int total = 0;
        for (String line : Files.readAllLines(new File(tsv).toPath(), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            if (parts.length < 3 || Integer.parseInt(parts[0].trim()) != bucket) {
                continue;
            }
            String id = parts[2].trim();
            String category = Category.guess(id);
            byCategory.computeIfAbsent(category, k -> new ArrayList<>())
                    .add(new ShortcutIndex.Entry(parts[1], id, category));
            total++;
        }

        List<ShortcutIndex.Group> groups = new ArrayList<>();
        int merged = 0;
        for (Map.Entry<String, List<ShortcutIndex.Entry>> entry : byCategory.entrySet()) {
            List<ShortcutIndex.Entry> list = ShortcutIndex.mergeNumbered(entry.getValue());
            merged += list.size();
            groups.add(new ShortcutIndex.Group(Category.displayName(entry.getKey()), list));
            System.out.println("  " + Category.displayName(entry.getKey()) + ": " + entry.getValue().size()
                    + (list.size() == entry.getValue().size() ? "" : " -> " + list.size()));
        }

        // Key names in the TSV are tokens copied straight out of the keymap XML and never pass
        // through keyText(), so spot-check the mapping table separately
        int[] probe = {KeyEvent.VK_BACK_SPACE, KeyEvent.VK_OPEN_BRACKET, KeyEvent.VK_CLOSE_BRACKET,
                KeyEvent.VK_SLASH, KeyEvent.VK_DIVIDE, KeyEvent.VK_SUBTRACT, KeyEvent.VK_MINUS,
                KeyEvent.VK_BACK_QUOTE, KeyEvent.VK_PAGE_UP, KeyEvent.VK_UP, KeyEvent.VK_NUMPAD1,
                KeyEvent.VK_ESCAPE, KeyEvent.VK_A, KeyEvent.VK_F12};
        StringBuilder mapped = new StringBuilder("key names: ");
        for (int code : probe) {
            mapped.append(KeyEvent.getKeyText(code)).append("->")
                    .append(ShortcutIndex.keyText(code)).append("  ");
        }
        System.out.println(mapped);

        String title = NextKeyBundle.message("hint.title.modifiers", MODIFIERS[bucket]);
        HintPanel panel = new HintPanel();
        Dimension size = panel.setContent(title,
                NextKeyBundle.message("hint.subtitle.count", merged), groups);
        panel.setSize(size);
        panel.doLayout();

        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g);
        g.dispose();
        ImageIO.write(image, "png", new File(out));

        System.out.println(title + "  " + total + " -> " + merged
                + "  panel=" + size.width + "x" + size.height);
    }
}
