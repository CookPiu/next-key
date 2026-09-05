package dev.nextkey;

import com.intellij.openapi.application.PathManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads and writes {@code next-key.conf} in the IDE config directory: which actions show up,
 * under what name, in which category, plus the popup's delay and opacity. The settings UI and
 * hand-editing the file share this one store; changes apply to the next popup.
 */
final class HintSettings {

    static final String FILE_NAME = "next-key.conf";

    /** The name this file had before the plugin was renamed in 0.3.0. One-off migration only. */
    private static final String LEGACY_FILE_NAME = "ctrl-hint.conf";

    /** Below this the popup is barely readable. */
    private static final float MIN_OPACITY = 0.2f;

    static final int MIN_DELAY_MS = 100;
    static final int MAX_DELAY_MS = 2000;
    static final int DEFAULT_DELAY_MS = 500;

    /**
     * Actions shown by default. Anything not listed starts out hidden and can be switched on
     * one by one, either in the settings UI or in the config file.
     */
    private static final Set<String> DEFAULT_VISIBLE = new HashSet<>(Arrays.asList(
            "$Copy", "$Cut", "$Paste", "$Redo", "$SelectAll", "$Undo",
            "Annotate", "Back", "CallHierarchy", "ChangeSignature", "CheckinProject",
            "ChooseDebugConfiguration", "ChooseRunConfiguration", "CloseAllEditors",
            "CloseContent", "CodeCompletion", "CollapseAllRegions", "CollapseRegion",
            "CommentByBlockComment", "CommentByLineComment", "CopyElement",
            "Debug", "DebugClass", "EditorCompleteStatement", "EditorDeleteLine",
            "EditorDeleteToWordEnd", "EditorDeleteToWordStart", "EditorDuplicate",
            "EditorJoinLines", "EditorSelectWord", "EditorToggleCase", "EditorUnSelectWord",
            "EvaluateExpression", "ExpandAllRegions", "ExpandRegion", "ExtractMethod",
            "FileStructurePopup", "Find", "FindInPath", "FindNext", "FindPrevious",
            "FindUsages", "ForceRunToCursor", "ForceStepInto", "Forward", "Generate",
            "GotoAction", "GotoBookmark0", "GotoBookmark1", "GotoBookmark2", "GotoBookmark3",
            "GotoBookmark4", "GotoBookmark5", "GotoBookmark6", "GotoBookmark7",
            "GotoBookmark8", "GotoBookmark9", "GotoClass", "GotoDeclaration", "GotoFile",
            "GotoImplementation", "GotoNextError", "GotoPreviousError", "GotoRelated",
            "GotoSuperMethod", "GotoSymbol", "GotoTypeDeclaration", "HideAllWindows",
            "HighlightUsagesInFile", "ImplementMethods", "Inline", "InsertLiveTemplate",
            "IntroduceConstant", "IntroduceField", "IntroduceParameter", "IntroduceVariable",
            "JumpToLastChange", "MaximizeToolWindow", "MethodHierarchy", "Move",
            "MoveStatementDown", "MoveStatementUp", "NextTab", "OptimizeImports",
            "OverrideMethods", "ParameterInfo", "PasteMultiple", "PreviousTab",
            "QuickEvaluateExpression", "QuickImplementations", "QuickJavaDoc",
            "RecentFiles", "RecentLocations", "Refactorings.QuickListPopupAction",
            "ReformatCode", "RenameElement", "Replace", "ReplaceInPath", "Resume",
            "Run", "RunClass", "RunToCursor", "SafeDelete", "SaveAll",
            "SelectAllOccurrences", "ShowBookmarks", "ShowIntentionActions", "ShowSettings",
            "ShowUsages", "SmartTypeCompletion", "StepInto", "StepOut", "StepOver", "Stop",
            "SurroundWith", "ToggleBookmark", "ToggleBookmarkWithMnemonic",
            "ToggleLineBreakpoint", "TypeHierarchy", "Vcs.Push", "Vcs.QuickListPopupAction",
            "Vcs.RollbackChangedLines", "Vcs.UpdateProject", "ViewBreakpoints"));

    /** How long the file timestamp is trusted before hitting the disk again. */
    private static final long STAMP_TTL_MS = 1000;

    private static volatile boolean migrated;
    private static volatile long stampCheckedAt;
    private static volatile long lastStamp;

    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, String> categories = new HashMap<>();
    private final Set<String> shown = new HashSet<>();
    private final Set<String> hidden = new HashSet<>();

    private boolean showAll;
    private boolean mergeNumbered = true;
    private float opacity = 1.0f;
    private int delayMs = DEFAULT_DELAY_MS;
    private boolean fromFile;

    private HintSettings() {
    }

    static File configFile() {
        try {
            File file = new File(PathManager.getConfigPath(), FILE_NAME);
            if (!migrated) {
                migrated = true;
                migrateLegacy(file);
            }
            return file;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The plugin was called Ctrl Hint before 0.3.0 and used a different file name. Move the old
     * file over so the rename does not throw away whatever the user had set up. This can go away
     * after a few releases.
     */
    private static void migrateLegacy(File target) {
        try {
            if (target.exists()) {
                return;
            }
            File legacy = new File(target.getParentFile(), LEGACY_FILE_NAME);
            if (legacy.isFile()) {
                legacy.renameTo(target);
            }
        } catch (Throwable ignored) {
            // Treat a failed migration as "no old config" and generate a fresh one
        }
    }

    /**
     * Last-modified time of the config file, used as part of the index cache key; 0 when there is
     * no file. Cached for {@link #STAMP_TTL_MS} because this sits on the key handling path — the
     * delay is read every time a modifier goes down, and Shift goes down constantly while typing.
     * The cost is that an edit to the file takes up to a second to be noticed.
     */
    static long stamp() {
        long now = System.currentTimeMillis();
        if (now - stampCheckedAt < STAMP_TTL_MS) {
            return lastStamp;
        }
        File file = configFile();
        lastStamp = file != null && file.isFile() ? file.lastModified() : 0L;
        stampCheckedAt = now;
        return lastStamp;
    }

    /** Drops the cached timestamp so the next read hits the disk. */
    static void invalidateStamp() {
        stampCheckedAt = 0;
    }

    static HintSettings load() {
        HintSettings settings = new HintSettings();
        File file = configFile();
        if (file == null || !file.isFile()) {
            return settings;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return settings;
        }
        settings.fromFile = true;
        for (String raw : lines) {
            settings.parse(raw);
        }
        return settings;
    }

    private void parse(String raw) {
        String line = raw;
        int comment = line.indexOf('#');
        if (comment >= 0) {
            line = line.substring(0, comment);
        }
        line = line.trim();
        if (line.isEmpty()) {
            return;
        }

        String key = line;
        String value = "";
        int eq = line.indexOf('=');
        if (eq >= 0) {
            key = line.substring(0, eq).trim();
            value = line.substring(eq + 1).trim();
        }
        if (key.isEmpty()) {
            return;
        }

        if ("show-all".equals(key)) {
            showAll = Boolean.parseBoolean(value);
            return;
        }
        if ("merge-numbered".equals(key)) {
            mergeNumbered = Boolean.parseBoolean(value);
            return;
        }
        if ("opacity".equals(key)) {
            try {
                setOpacity(Float.parseFloat(value));
            } catch (NumberFormatException ignored) {
                // Garbled value: keep the default
            }
            return;
        }
        if ("delay-ms".equals(key)) {
            try {
                setDelayMs(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                // Garbled value: keep the default
            }
            return;
        }

        String category = "";
        int bar = value.indexOf('|');
        if (bar >= 0) {
            category = value.substring(bar + 1).trim();
            value = value.substring(0, bar).trim();
        }

        boolean visible = key.charAt(0) != '-';
        String id = visible ? key : key.substring(1).trim();
        if (id.isEmpty()) {
            return;
        }
        if (visible) {
            shown.add(id);
        } else {
            hidden.add(id);
        }
        if (!value.isEmpty()) {
            labels.put(id, value);
        }
        if (!category.isEmpty()) {
            categories.put(id, category);
        }
    }

    boolean isVisible(String actionId) {
        if (showAll) {
            return true;
        }
        if (hidden.contains(actionId)) {
            return false;
        }
        if (shown.contains(actionId)) {
            return true;
        }
        // Actions the file says nothing about — from a newly installed plugin, say — still
        // go through the built-in list
        return DEFAULT_VISIBLE.contains(actionId);
    }

    /** Like {@link #isVisible} but ignores show-all; this is what the settings checkbox shows. */
    boolean isChecked(String actionId) {
        if (hidden.contains(actionId)) {
            return false;
        }
        if (shown.contains(actionId)) {
            return true;
        }
        return DEFAULT_VISIBLE.contains(actionId);
    }

    String label(String actionId, String defaultName) {
        String custom = labels.get(actionId);
        return custom == null || custom.isEmpty() ? defaultName : custom;
    }

    /** The user-supplied display name, or an empty string when there is none. */
    String rawLabel(String actionId) {
        String custom = labels.get(actionId);
        return custom == null ? "" : custom;
    }

    String category(String actionId) {
        String custom = categories.get(actionId);
        if (custom != null && !custom.isEmpty()) {
            return custom;
        }
        return Category.guess(actionId);
    }

    boolean isShowAll() {
        return showAll;
    }

    boolean isMergeNumbered() {
        return mergeNumbered;
    }

    boolean isFromFile() {
        return fromFile;
    }

    void setShowAll(boolean value) {
        showAll = value;
    }

    void setMergeNumbered(boolean value) {
        mergeNumbered = value;
    }

    float getOpacity() {
        return opacity;
    }

    void setOpacity(float value) {
        opacity = Math.max(MIN_OPACITY, Math.min(1.0f, value));
    }

    int getDelayMs() {
        return delayMs;
    }

    void setDelayMs(int value) {
        delayMs = Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, value));
    }

    void setChecked(String actionId, boolean value) {
        if (value) {
            hidden.remove(actionId);
            shown.add(actionId);
        } else {
            shown.remove(actionId);
            hidden.add(actionId);
        }
    }

    void setLabel(String actionId, String value) {
        if (value == null || value.trim().isEmpty()) {
            labels.remove(actionId);
        } else {
            labels.put(actionId, value.trim());
        }
    }

    void setCategory(String actionId, String value) {
        if (value == null || value.trim().isEmpty() || value.equals(Category.guess(actionId))) {
            categories.remove(actionId);
        } else {
            categories.put(actionId, value.trim());
        }
    }

    /**
     * Rewrites the whole config file from the current state. The raws are used to group the
     * entries by modifier, sort them, and put each action's shortcut and original name in a
     * trailing comment.
     */
    boolean save(List<ShortcutIndex.Raw> raws) {
        File file = configFile();
        if (file == null) {
            return false;
        }
        Map<Integer, Map<String, ShortcutIndex.Raw>> sections = new TreeMap<>();
        Set<String> seenIds = new HashSet<>();
        for (ShortcutIndex.Raw raw : raws) {
            if (!seenIds.add(raw.actionId)) {
                continue;
            }
            Map<String, ShortcutIndex.Raw> section = sections.get(raw.modifiers);
            if (section == null) {
                section = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                sections.put(raw.modifiers, section);
            }
            section.put(raw.key + " " + raw.actionId, raw);
        }

        List<String> lines = new ArrayList<>();
        String header = NextKeyBundle.message("conf.header", String.join(", ", Category.ALL));
        for (String line : header.split("\n")) {
            lines.add(line);
        }
        lines.add("");
        lines.add("show-all = " + showAll + "        " + NextKeyBundle.message("conf.showAll"));
        lines.add("merge-numbered = " + mergeNumbered + "   "
                + NextKeyBundle.message("conf.mergeNumbered"));
        lines.add("delay-ms = " + delayMs + "         " + NextKeyBundle.message("conf.delay"));
        lines.add("opacity = " + opacity + "         " + NextKeyBundle.message("conf.opacity"));
        lines.add("");

        for (Map.Entry<Integer, Map<String, ShortcutIndex.Raw>> section : sections.entrySet()) {
            lines.add("");
            lines.add("# ---- " + ShortcutIndex.modifiersLabel(section.getKey()) + " ----");
            for (ShortcutIndex.Raw raw : section.getValue().values()) {
                lines.add(line(raw));
            }
        }

        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            invalidateStamp();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private String line(ShortcutIndex.Raw raw) {
        StringBuilder head = new StringBuilder();
        if (!isChecked(raw.actionId)) {
            head.append('-');
        }
        head.append(raw.actionId);

        String label = rawLabel(raw.actionId);
        String category = categories.get(raw.actionId);
        if (!label.isEmpty() || category != null) {
            head.append(" = ").append(label);
            if (category != null) {
                head.append(label.isEmpty() ? "| " : " | ").append(category);
            }
        }

        String shortcut = ShortcutIndex.modifiersPrefix(raw.modifiers) + raw.key
                + (raw.secondLabel == null ? "" : ", " + raw.secondLabel);
        return pad(head.toString(), 48) + "# " + pad(shortcut, 22) + raw.name;
    }

    private static String pad(String text, int width) {
        if (text.length() >= width) {
            return text + " ";
        }
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
