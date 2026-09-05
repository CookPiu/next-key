# Next Key

[中文](README.zh-CN.md)

Hold a modifier in a JetBrains IDE and see what you can press next.

Hold `Ctrl` — or Alt, or Ctrl+Shift, or any other combination — for half a second and a panel
appears in the bottom right corner listing every shortcut that starts with it.

- **Follows the modifiers as you go.** Add `Alt` while the panel is up and it switches to the
  Ctrl+Alt list at once, without waiting again; release `Alt` and it switches back. The panel only
  goes away once every modifier is up.
- **Two-stroke shortcuts.** Press the first stroke of one (`Ctrl+Num *`, say) and every possible
  second stroke is listed immediately. Those prefix keys are marked `→ N more` in the modifier
  list, so it is obvious which keys lead somewhere.
- **Stays out of the way.** Pressing a key that is not a prefix dismisses the panel, and moving
  the mouse while it is still counting down cancels it — Ctrl+wheel zooming and Ctrl+hover
  inspection never raise it.

Entries are grouped by what the actions do (Editing, Navigation, Search, Refactor, Run & Debug,
Version Control, Windows & Tools, Other), guessed from how the action id is named and adjustable
per action.

Keys are labelled the way they are printed on the keyboard: `[`, `/`, `-`, `` ` ``, `↑`, `PgUp`,
`Num *`, rather than the `Open Bracket`, `Slash`, `Subtract`, `Back Quote`, `Up`, `Page Up`,
`Multiply` that `KeyEvent.getKeyText()` reports. That table also pins those names to English —
`getKeyText` resolves against the JVM locale and would otherwise translate them.

A full keymap hides several hundred shortcuts behind Ctrl alone, so only common actions are listed
by default (a built-in list of about 120), and duplicates are folded: one action bound to several
keys becomes `C,Ins`, and the ten bookmark jumps on Ctrl+0..9 collapse into a single `0-9` row.

## Configuration

**Settings UI** — `Settings | Tools | Next Key`. The table lists every shortcut in the active
keymap that starts with a modifier. Tick one to show it, rename it under *Display as*, pick a
category from the dropdown or type your own. Above the table are two global switches, the hold
delay, the panel opacity and a filter box. Applying rewrites the config file in full.

**Config file** — `next-key.conf` in the IDE config directory (on Windows,
`%APPDATA%\JetBrains\<IDE><version>\`). It is the same store the settings UI uses, so editing it
by hand works just as well. It is generated from the active keymap on first run and grouped by
modifier:

```
show-all = false        # true shows every shortcut and ignores the per-action switches below
merge-numbered = true   # merge actions that differ only by a trailing number, such as bookmarks 0-9
delay-ms = 500          # how long a modifier must be held before the popup appears, 100-2000 ms
opacity = 1.0           # popup opacity, from 0.2 to 1.0

# ---- Ctrl ----
EditorDuplicate                     # Ctrl+D       Duplicate Line
-EditorLookupUp                     # Ctrl+↑       Lookup Up
GotoDeclaration = Jump to source    # Ctrl+B       Go to Declaration
ShowSettings = Settings | Windows   # Ctrl+Alt+S   Settings
```

Four forms per line: `<id>` shows it, `-<id>` hides it, `<id> = Name` renames it, and
`<id> = Name | Category` also moves it (leave the name empty as `<id> = | Category`). Everything
after `#` is a comment, and the comment on each line carries that action's shortcut and original
name, so there is no need to look up an action id elsewhere. Categories are stored as the stable
English values (`Editing`, `Navigation`, …); any other text becomes a category of its own.

Changes apply to the next popup, no restart — the index cache keys on the file's timestamp.

## Building

For day-to-day changes use `build.ps1`. It needs no Gradle and no SDK: it compiles with the JBR
that ships inside the target IDE, pointing the classpath at that IDE's `lib` directory, and
downloads nothing. To produce a submittable plugin archive (and to run the Plugin Verifier) use
Gradle — see [PUBLISHING.md](PUBLISHING.md).

```powershell
.\build.ps1              # produces build\next-key.jar
.\build.ps1 -Install     # also installs into the IDE; restart to pick it up
```

Installing into a different JetBrains IDE:

```powershell
.\build.ps1 -Ide "D:\Applications\IntelliJ IDEA 2025.2" -Config IntelliJIdea2025.2 -Install
```

`-Config` is the directory name under `%APPDATA%\JetBrains\`. The install target is not
necessarily `plugins` under that directory: the script first reads `idea.plugins.path` out of
`%APPDATA%\JetBrains\<Config>\idea.properties` (then the IDE's own `bin\idea.properties`) and
honours the redirect if there is one.

## How it works

Keys are watched with `KeyboardFocusManager.addKeyEventDispatcher`. The dispatcher always returns
`false` and never consumes an event, so it cannot interfere with typing. Mouse events come from
`Toolkit.addAWTEventListener` and are only used to suppress the popup while it is counting down.
Both are public JDK API; no platform internals are involved.

The modifiers currently held come from `KeyEvent.getModifiersEx()`, with the pressed bit OR'd in
on the way down and cleared by hand on the way up, rather than relying on what AWT reports for a
KEY_RELEASED event.

The panel lives in a `JWindow` rather than a `JBPopup`: the content changes as soon as the modifier
combination does, and a JBPopup can only be cancelled and rebuilt, which flickers on every swap.

The index is built on a pooled thread at startup. Walking the keymap to read every action's display
name takes a few hundred milliseconds, and doing that on the first keypress would block the EDT —
by the time the popup appeared, the queued KEY_RELEASED would already be closing it again.

## Tuning constants

| Where | Constant | Default | Meaning |
|---|---|---|---|
| `NextKeyController` | `AUTO_HIDE_MS` | 10000 | Fallback hide, in case a modifier's KEY_RELEASED never arrives |
| `HintSettings` | `MIN_DELAY_MS` / `MAX_DELAY_MS` | 100 / 2000 | Range the configurable hold delay is clamped to |
| `HintSettings` | `STAMP_TTL_MS` | 1000 | How long the config file's timestamp is trusted before hitting the disk |
| `HintSettings` | `DEFAULT_VISIBLE` | ~120 ids | Built-in list of common actions, deciding which lines start out unhidden |
| `HintPanel` | `PAD` / `COL_GAP` / `KEY_GAP` | 14 / 22 / 12 | Padding and gaps |
| `HintPanel` | `KEY_PAD` / `KEY_RADIUS` / `CAP_INSET` | 7 / 4 / 2 | Keycap padding, corner radius and inset |
| `HintPanel` | `CORNER_RADIUS` | 10 | Popup corner radius, reused by `HintWindow` to clip the window |
| `HintPanel` | `MAX_NAME_WIDTH` | 240 | Action names are truncated past this, so one cannot widen a column |
| `HintWindow` | `MARGIN_RIGHT` / `MARGIN_BOTTOM` | 40 / 60 | Offset from the active window's bottom right corner |
| `ShortcutIndex` | `MERGE_THRESHOLD` | 3 | How many trailing-number siblings are needed before they fold |

The panel works out the minimum number of columns from the screen height (at most 84% of its height
and 92% of its width), then splits by average height so the last column does not end up holding one
or two entries; a category heading never lands at the bottom of a column. Columns that do not fit
the width are dropped and noted in the subtitle.

## Checking the layout

`tools/RenderTest.java` renders the panel to a PNG offline from a TSV of bucket, key and action id:

```powershell
$Ide = "D:\Applications\PyCharm 2026.2.1"
& "$Ide\jbr\bin\javac.exe" --release 21 -encoding UTF-8 -cp "build\classes;$Ide\lib\*" -d build\test-classes tools\RenderTest.java
& "$Ide\jbr\bin\java.exe" "-Dstdout.encoding=UTF-8" -cp "build\classes;build\test-classes;$Ide\lib\*" dev.nextkey.RenderTest shortcuts.tsv out.png dark 0
```

The last argument is the bucket: 0=Ctrl, 1=Ctrl+Shift, 2=Ctrl+Alt, 3=Ctrl+Alt+Shift. The classpath
needs the IDE's `lib` because the panel resolves localized category names, and that path ends at
the platform's `DynamicBundle`. Outside an IDE there is no interface language to read, so it falls
back to English.

The settings UI cannot be rendered this way — `JBTable` throws `Must be precomputed` when it is
initialized outside an IDE, so it has to be checked by installing the plugin.

## Interface language

English by default, with strings under `src/main/resources/messages/`. The language follows the
**IDE language setting** (Settings | Appearance & Behavior | System Settings | Language and
Region) via `DynamicBundle.getLocale()`, not the operating system locale — plenty of people run an
English IDE on a non-English system, and following the wrong one has the plugin and the IDE
speaking different languages.

Bundle loading passes `ResourceBundle.Control.getNoFallbackControl`. The default search order is
"requested locale, then **JVM default locale**, then the base bundle", and that middle step would
hand Chinese strings to an English IDE running on a Chinese system. It is a hard one to notice when
testing on such a system, because both locales happen to agree.

Category values are stored in English (`Editing`, `Navigation`, …) and localized only for display,
so switching languages never invalidates what is in the config file.

## Known limitations

- Editing a shortcut within the same keymap needs an IDE restart to show up; switching keymaps
  rebuilds the index on its own. This is the trade for not subscribing to the platform's keymap
  change events, see `ShortcutIndex.get()`.
- Holding Shift on its own also triggers the panel. Shift-only shortcuts are rare, and while
  typing a letter key follows immediately and cancels the countdown, so it seldom comes up.
- The panel hides itself after `AUTO_HIDE_MS`, guarding against a modifier's KEY_RELEASED being
  lost when focus moves to another application.
- The built-in list of common actions is written against the platform's own action ids; actions
  from IDE-specific plugins are not in it and have to be switched on in `next-key.conf`.
- Shortcuts a newly installed plugin brings in are not added to an existing `next-key.conf`. They
  are judged by the built-in list instead, which mostly means hidden. Delete the file and restart
  to regenerate it.
- Opacity depends on window compositing and does nothing over remote desktop or under some Linux
  compositors, where the panel simply stays opaque; rounded corners degrade to square the same way.
- When a whole category does not fit, column splitting falls back to filling to the screen height,
  which can leave the columns visibly uneven. Truly even columns would mean breaking a category
  across two columns, which reads worse.

## Uninstalling

Delete the `next-key` directory from the IDE's plugins directory and restart.
