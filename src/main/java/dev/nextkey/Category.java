package dev.nextkey;

/**
 * Categories the popup groups entries into. Guessed from how the action id is named; a wrong
 * guess can be corrected in the settings UI, and the correction sticks once it lands in
 * next-key.conf.
 * <p>
 * The constants are the stable values written to the config file (English); localization happens
 * only at display time via {@link #displayName}. A category the user typed themselves is not in
 * this table and is stored and shown verbatim.
 */
final class Category {

    static final String EDIT = "Editing";
    static final String NAVIGATE = "Navigation";
    static final String SEARCH = "Search";
    static final String REFACTOR = "Refactor";
    static final String RUN = "Run";
    static final String VCS = "VCS";
    static final String WINDOW = "Windows";
    static final String OTHER = "Other";

    /** The order sections appear in the popup. */
    static final String[] ALL = {EDIT, NAVIGATE, SEARCH, REFACTOR, RUN, VCS, WINDOW, OTHER};

    private Category() {
    }

    static int order(String category) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].equals(category)) {
                return i;
            }
        }
        return ALL.length;
    }

    /** Localized display name; anything that is not a built-in category comes back unchanged. */
    static String displayName(String category) {
        if (EDIT.equals(category)) {
            return NextKeyBundle.message("category.edit");
        }
        if (NAVIGATE.equals(category)) {
            return NextKeyBundle.message("category.navigate");
        }
        if (SEARCH.equals(category)) {
            return NextKeyBundle.message("category.search");
        }
        if (REFACTOR.equals(category)) {
            return NextKeyBundle.message("category.refactor");
        }
        if (RUN.equals(category)) {
            return NextKeyBundle.message("category.run");
        }
        if (VCS.equals(category)) {
            return NextKeyBundle.message("category.vcs");
        }
        if (WINDOW.equals(category)) {
            return NextKeyBundle.message("category.window");
        }
        if (OTHER.equals(category)) {
            return NextKeyBundle.message("category.other");
        }
        return category;
    }

    /** The settings dropdown offers display names; turn one back into the stable value. */
    static String fromDisplayName(String displayName) {
        for (String category : ALL) {
            if (displayName(category).equals(displayName)) {
                return category;
            }
        }
        return displayName;
    }

    static String[] displayNames() {
        String[] names = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) {
            names[i] = displayName(ALL[i]);
        }
        return names;
    }

    /**
     * The order of these checks matters. VCS and run/debug actions have the most distinctive
     * names, so they get taken out first. Refactoring's Move has to match exactly, otherwise it
     * would also swallow MoveStatementUp, which belongs to editing.
     */
    static String guess(String actionId) {
        String id = actionId;
        if (startsWithAny(id, "Vcs.", "Git.", "Svn.", "Hg.", "ChangesView", "Compare", "Diff.",
                "Annotate", "CheckinProject", "Subversion")) {
            return VCS;
        }
        if (startsWithAny(id, "XDebugger.", "Debugger.", "Run", "Debug", "Step", "Force",
                "Resume", "Pause", "Stop", "ToggleLineBreakpoint", "ViewBreakpoints",
                "EvaluateExpression", "QuickEvaluateExpression", "Choose", "Coverage",
                "editRunConfigurations", "Compile", "MakeModule", "BuildProject")) {
            return RUN;
        }
        if (startsWithAny(id, "Find", "Replace", "SearchEverywhere", "IncrementalSearch",
                "ShowUsages", "HighlightUsagesInFile", "GotoAction", "ShowFilterPopup")) {
            return SEARCH;
        }
        if (startsWithAny(id, "Rename", "Introduce", "Extract", "Inline", "SafeDelete",
                "ChangeSignature", "Refactor", "CopyElement", "SurroundWith", "MakeStatic",
                "Encapsulate", "ConvertTo", "Migrate")
                || id.equals("Move") || id.startsWith("Move.")) {
            return REFACTOR;
        }
        if (id.contains("ToolWindow")
                || startsWithAny(id, "Activate", "Hide", "Maximize", "ShowSettings",
                "ShowProjectStructureSettings", "CloseContent", "CloseAll", "CloseActiveTab",
                "Split", "NextProjectWindow", "PreviousProjectWindow", "ToggleFullScreen",
                "TogglePresentationMode", "ChangeView", "QuickChangeScheme", "SelectIn",
                "ShowPopupMenu", "ViewNavigationBar")) {
            return WINDOW;
        }
        if (startsWithAny(id, "Goto", "Back", "Forward", "Recent", "MethodHierarchy",
                "TypeHierarchy", "CallHierarchy", "FileStructurePopup", "JumpTo", "NextTab",
                "PreviousTab", "ToggleBookmark", "ShowBookmarks", "FindUsages", "NextOccurence",
                "PreviousOccurence", "NextDiff", "PreviousDiff")) {
            return NAVIGATE;
        }
        if (startsWithAny(id, "Editor", "Comment", "Reformat", "OptimizeImports", "MoveStatement",
                "MoveLine", "CodeCompletion", "SmartTypeCompletion", "ShowIntentionActions",
                "Generate", "Override", "Implement", "InsertLiveTemplate", "ExpandLiveTemplate",
                "Expand", "Collapse", "SelectAllOccurrences", "SelectNextOccurrence",
                "UnselectPreviousOccurrence", "ParameterInfo", "QuickJavaDoc",
                "QuickImplementations", "SaveAll", "SaveDocument", "Paste", "NewElement",
                "$")) {
            return EDIT;
        }
        return OTHER;
    }

    private static boolean startsWithAny(String id, String... prefixes) {
        for (String prefix : prefixes) {
            if (id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
