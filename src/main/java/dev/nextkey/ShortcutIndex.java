package dev.nextkey;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns the active keymap into two lookup tables:
 * <ul>
 *   <li>{@code byModifiers} maps a modifier combination to every main key under it, which is
 *       what "hold Ctrl+Alt to see what comes next" reads from;</li>
 *   <li>{@code bySecondPrefix} maps the first stroke of a two-stroke shortcut to every possible
 *       second stroke, which is what shows up after Ctrl+K.</li>
 * </ul>
 * Entries in both tables are filtered and renamed through {@link HintSettings}, grouped by
 * {@link Category}, and within a group those that differ only by a trailing number (bookmarks on
 * Ctrl+0..9, say) are folded into a single row. Results are cached against the keymap name and
 * the config file timestamp.
 */
final class ShortcutIndex {

    /** One candidate: key is what still has to be pressed, name is the action. */
    static final class Entry {
        final String key;
        final String name;
        final String category;

        Entry(String key, String name, String category) {
            this.key = key;
            this.name = name;
            this.category = category;
        }
    }

    /** One category section in the panel. */
    static final class Group {
        final String title;
        final List<Entry> entries;

        Group(String title, List<Entry> entries) {
            this.title = title;
            this.entries = entries;
        }
    }

    static final class Data {
        final Map<Integer, List<Group>> byModifiers;
        final Map<String, List<Group>> bySecondPrefix;

        Data(Map<Integer, List<Group>> byModifiers, Map<String, List<Group>> bySecondPrefix) {
            this.byModifiers = byModifiers;
            this.bySecondPrefix = bySecondPrefix;
        }
    }

    /** A raw entry as collected, before filtering and renaming. The settings table reads these too. */
    static final class Raw {
        final int modifiers;
        final int keyCode;
        final String key;
        final String actionId;
        final String name;
        final String secondLabel;

        Raw(int modifiers, int keyCode, String key, String actionId, String name, String secondLabel) {
            this.modifiers = modifiers;
            this.keyCode = keyCode;
            this.key = key;
            this.actionId = actionId;
            this.name = name;
            this.secondLabel = secondLabel;
        }

        /** "Ctrl+K", or "Ctrl+Num *, 1" for a two-stroke shortcut. */
        String shortcutText() {
            return modifiersPrefix(modifiers) + key + (secondLabel == null ? "" : ", " + secondLabel);
        }
    }

    // Only the *_DOWN_MASK constants are used internally
    static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    static final int ALT = InputEvent.ALT_DOWN_MASK;
    static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    static final int META = InputEvent.META_DOWN_MASK;
    static final int MODIFIER_MASK = CTRL | ALT | SHIFT | META;

    // A KeyStroke may still carry the JDK 1.1 era masks; match them by value to avoid
    // referencing the deprecated constants
    private static final int LEGACY_SHIFT = 0x1;
    private static final int LEGACY_CTRL = 0x2;
    private static final int LEGACY_META = 0x4;
    private static final int LEGACY_ALT = 0x8;

    /** Folding needs at least this many trailing-number siblings, so unrelated pairs stay apart. */
    private static final int MERGE_THRESHOLD = 3;

    private static final Logger LOG = Logger.getInstance(ShortcutIndex.class);

    private static volatile Data cache;
    private static volatile String cacheKeymap;
    private static volatile long cacheStamp = -1;
    private static volatile HintSettings cacheSettings;

    private ShortcutIndex() {
    }

    static void invalidate() {
        cache = null;
        cacheStamp = -1;
    }

    /**
     * Rebuilt automatically when the keymap is switched or the config file changes. Editing a
     * shortcut within the same keymap still needs an IDE restart — the trade for not subscribing
     * to the platform's keymap change events.
     */
    static Data get() {
        String name = activeKeymapName();
        long stamp = HintSettings.stamp();
        Data local = cache;
        if (local == null || !name.equals(cacheKeymap) || stamp != cacheStamp) {
            local = build();
            cache = local;
            cacheKeymap = name;
            cacheStamp = stamp;
        }
        return local;
    }

    /** The settings the index was built with, reused for delay and opacity so that showing the
     * popup does not re-read the file. */
    static HintSettings settings() {
        get();
        HintSettings local = cacheSettings;
        return local != null ? local : HintSettings.load();
    }

    static int count(List<Group> groups) {
        int total = 0;
        for (Group group : groups) {
            total += group.entries.size();
        }
        return total;
    }

    /** Lookup key for the first stroke of a two-stroke shortcut. */
    static String prefixKey(int modifiers, int keyCode) {
        return (modifiers & MODIFIER_MASK) + ":" + keyCode;
    }

    /** For the panel title: "Ctrl + Alt". */
    static String modifiersLabel(int modifiers) {
        StringBuilder sb = new StringBuilder();
        appendModifier(sb, modifiers, CTRL, "Ctrl", " + ");
        appendModifier(sb, modifiers, ALT, "Alt", " + ");
        appendModifier(sb, modifiers, SHIFT, "Shift", " + ");
        appendModifier(sb, modifiers, META, "Win", " + ");
        return sb.toString();
    }

    /** For prefixing an entry: "Ctrl+Alt+", or an empty string with no modifiers. */
    static String modifiersPrefix(int modifiers) {
        StringBuilder sb = new StringBuilder();
        appendModifier(sb, modifiers, CTRL, "Ctrl", "+");
        appendModifier(sb, modifiers, ALT, "Alt", "+");
        appendModifier(sb, modifiers, SHIFT, "Shift", "+");
        appendModifier(sb, modifiers, META, "Win", "+");
        return sb.length() == 0 ? "" : sb.append('+').toString();
    }

    private static void appendModifier(StringBuilder sb, int mods, int mask, String text, String sep) {
        if ((mods & mask) == 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(sep);
        }
        sb.append(text);
    }

    /**
     * KeyEvent.getKeyText hands back internal names like "Back Space", "Open Bracket" and
     * "Subtract", which is not what the keyboard says. This maps the common keys to what is
     * actually printed on them.
     */
    private static final Map<Integer, String> KEY_NAMES = new HashMap<>();

    static {
        // getKeyText resolves against the JVM locale, so on a Chinese system it returns
        // translated names that do not match the IDE's own language. Every key whose name might
        // be translated is pinned to its English form here.
        KEY_NAMES.put(KeyEvent.VK_ENTER, "Enter");
        KEY_NAMES.put(KeyEvent.VK_SPACE, "Space");
        KEY_NAMES.put(KeyEvent.VK_TAB, "Tab");
        KEY_NAMES.put(KeyEvent.VK_HOME, "Home");
        KEY_NAMES.put(KeyEvent.VK_END, "End");
        KEY_NAMES.put(KeyEvent.VK_CAPS_LOCK, "CapsLock");
        KEY_NAMES.put(KeyEvent.VK_NUM_LOCK, "NumLock");
        KEY_NAMES.put(KeyEvent.VK_SCROLL_LOCK, "ScrLk");
        KEY_NAMES.put(KeyEvent.VK_PRINTSCREEN, "PrtSc");
        KEY_NAMES.put(KeyEvent.VK_PAUSE, "Pause");
        KEY_NAMES.put(KeyEvent.VK_CONTEXT_MENU, "Menu");
        KEY_NAMES.put(KeyEvent.VK_BACK_SPACE, "Backspace");
        KEY_NAMES.put(KeyEvent.VK_ESCAPE, "Esc");
        KEY_NAMES.put(KeyEvent.VK_DELETE, "Del");
        KEY_NAMES.put(KeyEvent.VK_INSERT, "Ins");
        KEY_NAMES.put(KeyEvent.VK_PAGE_UP, "PgUp");
        KEY_NAMES.put(KeyEvent.VK_PAGE_DOWN, "PgDn");
        KEY_NAMES.put(KeyEvent.VK_UP, "↑");
        KEY_NAMES.put(KeyEvent.VK_DOWN, "↓");
        KEY_NAMES.put(KeyEvent.VK_LEFT, "←");
        KEY_NAMES.put(KeyEvent.VK_RIGHT, "→");
        KEY_NAMES.put(KeyEvent.VK_OPEN_BRACKET, "[");
        KEY_NAMES.put(KeyEvent.VK_CLOSE_BRACKET, "]");
        KEY_NAMES.put(KeyEvent.VK_BACK_QUOTE, "`");
        KEY_NAMES.put(KeyEvent.VK_QUOTE, "'");
        KEY_NAMES.put(KeyEvent.VK_SEMICOLON, ";");
        KEY_NAMES.put(KeyEvent.VK_COMMA, ",");
        KEY_NAMES.put(KeyEvent.VK_PERIOD, ".");
        KEY_NAMES.put(KeyEvent.VK_SLASH, "/");
        KEY_NAMES.put(KeyEvent.VK_BACK_SLASH, "\\");
        KEY_NAMES.put(KeyEvent.VK_MINUS, "-");
        KEY_NAMES.put(KeyEvent.VK_EQUALS, "=");
        // Numpad operators are marked so they cannot be mistaken for the main-row keys
        KEY_NAMES.put(KeyEvent.VK_MULTIPLY, "Num *");
        KEY_NAMES.put(KeyEvent.VK_DIVIDE, "Num /");
        KEY_NAMES.put(KeyEvent.VK_ADD, "Num +");
        KEY_NAMES.put(KeyEvent.VK_SUBTRACT, "Num -");
        KEY_NAMES.put(KeyEvent.VK_DECIMAL, "Num .");
        for (int i = 0; i <= 9; i++) {
            KEY_NAMES.put(KeyEvent.VK_NUMPAD0 + i, "Num " + i);
        }
    }

    static String keyText(int keyCode) {
        String mapped = KEY_NAMES.get(keyCode);
        if (mapped != null) {
            return mapped;
        }
        String text = KeyEvent.getKeyText(keyCode);
        // getKeyText returns "Unknown keyCode: 0x.." for keys it does not know; fall back to
        // a short hex form
        return text.startsWith("Unknown") ? "0x" + Integer.toHexString(keyCode) : text;
    }

    static boolean isModifierKey(int keyCode) {
        return keyCode == KeyEvent.VK_CONTROL
                || keyCode == KeyEvent.VK_ALT
                || keyCode == KeyEvent.VK_SHIFT
                || keyCode == KeyEvent.VK_META
                || keyCode == KeyEvent.VK_ALT_GRAPH
                || keyCode == KeyEvent.VK_UNDEFINED;
    }

    /** The down mask for a modifier key code, used to clear it by hand on KEY_RELEASED. */
    static int maskOf(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_CONTROL:
                return CTRL;
            case KeyEvent.VK_ALT:
            case KeyEvent.VK_ALT_GRAPH:
                return ALT;
            case KeyEvent.VK_SHIFT:
                return SHIFT;
            case KeyEvent.VK_META:
                return META;
            default:
                return 0;
        }
    }

    static int normalize(int modifiers) {
        int result = 0;
        if ((modifiers & (CTRL | LEGACY_CTRL)) != 0) {
            result |= CTRL;
        }
        if ((modifiers & (ALT | LEGACY_ALT)) != 0) {
            result |= ALT;
        }
        if ((modifiers & (SHIFT | LEGACY_SHIFT)) != 0) {
            result |= SHIFT;
        }
        if ((modifiers & (META | LEGACY_META)) != 0) {
            result |= META;
        }
        return result;
    }

    private static Data build() {
        long start = System.currentTimeMillis();
        Data data = doBuild();
        LOG.info("[next-key] index built in " + (System.currentTimeMillis() - start) + " ms");
        return data;
    }

    private static Data doBuild() {
        List<Raw> raws = collect();
        HintSettings settings = HintSettings.load();
        cacheSettings = settings;
        if (!settings.isFromFile()) {
            settings.save(raws);
        }

        Map<Integer, List<Entry>> flatByModifiers = new HashMap<>();
        Map<String, List<Entry>> flatBySecond = new HashMap<>();
        Set<String> seen = new HashSet<>();

        for (Raw raw : raws) {
            if (!settings.isVisible(raw.actionId)) {
                continue;
            }
            String name = settings.label(raw.actionId, raw.name);
            String category = settings.category(raw.actionId);
            if (raw.secondLabel == null) {
                if (seen.add("1|" + raw.modifiers + "|" + raw.keyCode)) {
                    bucket(flatByModifiers, raw.modifiers).add(new Entry(raw.key, name, category));
                }
            } else {
                String prefix = prefixKey(raw.modifiers, raw.keyCode);
                if (seen.add("2|" + prefix + "|" + raw.secondLabel)) {
                    bucket(flatBySecond, prefix).add(new Entry(raw.secondLabel, name, category));
                }
            }
        }

        // The first stroke of a two-stroke shortcut has to appear in the modifier list as well,
        // otherwise there is no way to tell it leads somewhere
        for (Map.Entry<String, List<Entry>> prefix : flatBySecond.entrySet()) {
            int split = prefix.getKey().indexOf(':');
            int mods = Integer.parseInt(prefix.getKey().substring(0, split));
            int code = Integer.parseInt(prefix.getKey().substring(split + 1));
            if (mods == 0 || !seen.add("1|" + mods + "|" + code)) {
                continue;
            }
            bucket(flatByModifiers, mods).add(new Entry(keyText(code),
                    NextKeyBundle.message("hint.entry.more", prefix.getValue().size()),
                    Category.OTHER));
        }

        Map<Integer, List<Group>> byModifiers = new HashMap<>();
        for (Map.Entry<Integer, List<Entry>> bucket : flatByModifiers.entrySet()) {
            byModifiers.put(bucket.getKey(), group(bucket.getValue(), settings));
        }
        Map<String, List<Group>> bySecondPrefix = new HashMap<>();
        for (Map.Entry<String, List<Entry>> bucket : flatBySecond.entrySet()) {
            bySecondPrefix.put(bucket.getKey(), group(bucket.getValue(), settings));
        }
        return new Data(byModifiers, bySecondPrefix);
    }

    /** Groups by category, folding number sequences and sorting within each; categories follow
     * the order in {@link Category#ALL}. */
    private static List<Group> group(List<Entry> entries, HintSettings settings) {
        Map<String, List<Entry>> byCategory = new TreeMap<>(new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int r = Category.order(a) - Category.order(b);
                return r != 0 ? r : a.compareTo(b);
            }
        });
        for (Entry entry : entries) {
            List<Entry> list = byCategory.get(entry.category);
            if (list == null) {
                list = new ArrayList<>();
                byCategory.put(entry.category, list);
            }
            list.add(entry);
        }

        Comparator<Entry> byKey = new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                int r = sortWeight(a.key) - sortWeight(b.key);
                return r != 0 ? r : String.CASE_INSENSITIVE_ORDER.compare(a.key, b.key);
            }
        };
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> bucket : byCategory.entrySet()) {
            List<Entry> list = settings.isMergeNumbered()
                    ? mergeNumbered(bucket.getValue())
                    : bucket.getValue();
            list.sort(byKey);
            groups.add(new Group(Category.displayName(bucket.getKey()), list));
        }
        return groups;
    }

    /** Collects every shortcut in the active keymap that starts with a modifier, unfiltered. */
    static List<Raw> collect() {
        List<Raw> raws = new ArrayList<>();
        KeymapManager keymapManager;
        ActionManager actionManager;
        try {
            keymapManager = KeymapManager.getInstance();
            actionManager = ActionManager.getInstance();
        } catch (Throwable t) {
            // Before the platform is ready these getInstance calls throw rather than return null
            return raws;
        }
        if (keymapManager == null || actionManager == null) {
            return raws;
        }
        Keymap keymap = keymapManager.getActiveKeymap();
        if (keymap == null) {
            return raws;
        }
        Collection<String> ids;
        try {
            ids = keymap.getActionIdList();
        } catch (Throwable t) {
            return raws;
        }

        for (String id : ids) {
            Shortcut[] shortcuts;
            try {
                shortcuts = keymap.getShortcuts(id);
            } catch (Throwable t) {
                continue;
            }
            for (Shortcut shortcut : shortcuts) {
                if (!(shortcut instanceof KeyboardShortcut)) {
                    continue;
                }
                KeyboardShortcut ks = (KeyboardShortcut) shortcut;
                KeyStroke first = ks.getFirstKeyStroke();
                int mods = normalize(first.getModifiers());
                int code = first.getKeyCode();
                if (isModifierKey(code)) {
                    continue;
                }
                String name = displayName(actionManager, id);
                if (name == null) {
                    continue;
                }
                KeyStroke second = ks.getSecondKeyStroke();
                if (second == null) {
                    // Shortcuts without a modifier (F5, Escape) have no prefix to hold, so skip
                    if (mods == 0) {
                        continue;
                    }
                    raws.add(new Raw(mods, code, keyText(code), id, name, null));
                } else {
                    String label = modifiersPrefix(normalize(second.getModifiers()))
                            + keyText(second.getKeyCode());
                    raws.add(new Raw(mods, code, keyText(code), id, name, label));
                }
            }
        }
        return raws;
    }

    /**
     * Folds actions that differ only by a trailing number into one row: the ten bookmark jumps
     * on Ctrl+0..9 become "0-9 Go to Bookmark". Fewer than {@link #MERGE_THRESHOLD} siblings are
     * left alone.
     */
    static List<Entry> mergeNumbered(List<Entry> entries) {
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        List<Entry> result = new ArrayList<>();
        for (Entry entry : collapseSameName(entries)) {
            String stem = stemOf(entry.name);
            if (entry.key.length() != 1 || stem == null) {
                result.add(entry);
                continue;
            }
            List<Entry> group = groups.get(stem);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(stem, group);
            }
            group.add(entry);
        }
        for (Map.Entry<String, List<Entry>> group : groups.entrySet()) {
            List<Entry> list = group.getValue();
            if (list.size() < MERGE_THRESHOLD) {
                result.addAll(list);
            } else {
                result.add(new Entry(rangeLabel(list), group.getKey(), list.get(0).category));
            }
        }
        return result;
    }

    /**
     * Folds one action bound to several keys into a single row with the keys joined by commas,
     * so Copy on both Ctrl+C and Ctrl+Ins shows as "C,Ins". The folded key is no longer a single
     * character, which keeps it out of the number-sequence folding above.
     */
    private static List<Entry> collapseSameName(List<Entry> entries) {
        Map<String, List<Entry>> byName = new LinkedHashMap<>();
        for (Entry entry : entries) {
            List<Entry> list = byName.get(entry.name);
            if (list == null) {
                list = new ArrayList<>();
                byName.put(entry.name, list);
            }
            list.add(entry);
        }
        List<Entry> result = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> group : byName.entrySet()) {
            List<Entry> list = group.getValue();
            if (list.size() == 1) {
                result.add(list.get(0));
                continue;
            }
            StringBuilder keys = new StringBuilder();
            for (int i = 0; i < list.size() && i < 3; i++) {
                if (keys.length() > 0) {
                    keys.append(',');
                }
                keys.append(list.get(i).key);
            }
            if (list.size() > 3) {
                keys.append(",…");
            }
            result.add(new Entry(keys.toString(), group.getKey(), list.get(0).category));
        }
        return result;
    }

    /** Strips a trailing number off an action name; null when there is none, meaning it does
     * not take part in folding. */
    private static String stemOf(String name) {
        int end = name.length();
        while (end > 0 && Character.isDigit(name.charAt(end - 1))) {
            end--;
        }
        if (end == name.length()) {
            return null;
        }
        while (end > 0 && name.charAt(end - 1) == ' ') {
            end--;
        }
        return end == 0 ? null : name.substring(0, end);
    }

    /** A contiguous run becomes "0-9"; anything else is listed key by key. */
    private static String rangeLabel(List<Entry> entries) {
        TreeMap<Character, Entry> keys = new TreeMap<>();
        for (Entry entry : entries) {
            keys.put(entry.key.charAt(0), entry);
        }
        char first = keys.firstKey();
        char last = keys.lastKey();
        if (last - first == keys.size() - 1) {
            return first + "-" + last;
        }
        StringBuilder sb = new StringBuilder();
        for (Character c : keys.keySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(c.charValue());
        }
        return sb.toString();
    }

    private static <K> List<Entry> bucket(Map<K, List<Entry>> map, K key) {
        List<Entry> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }
        return list;
    }

    /** Single characters and folded ranges sort first, then function keys, then the rest. */
    private static int sortWeight(String key) {
        char head = key.charAt(0);
        if (key.length() == 1) {
            return Character.isLetterOrDigit(head) ? 0 : 1;
        }
        if (head == 'F' && Character.isDigit(key.charAt(1))) {
            return 2;
        }
        if (Character.isLetterOrDigit(head) && (key.indexOf('-') > 0 || key.indexOf(',') > 0)) {
            return 0;
        }
        return 3;
    }

    private static String displayName(ActionManager actionManager, String id) {
        AnAction action;
        try {
            action = actionManager.getAction(id);
        } catch (Throwable t) {
            return null;
        }
        if (action == null) {
            return null;
        }
        String text;
        try {
            text = action.getTemplatePresentation().getText();
        } catch (Throwable t) {
            return null;
        }
        if (text == null) {
            return null;
        }
        text = text.replace("_", "").trim();
        return text.isEmpty() ? null : text;
    }

    private static String activeKeymapName() {
        try {
            KeymapManager manager = KeymapManager.getInstance();
            if (manager != null) {
                Keymap keymap = manager.getActiveKeymap();
                if (keymap != null) {
                    return keymap.getName();
                }
            }
        } catch (Throwable ignored) {
            // The keymap subsystem may not exist yet during very early calls
        }
        return "";
    }
}
