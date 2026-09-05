# Next Key

[![License](https://img.shields.io/github/license/CookPiu/next-key)](LICENSE)

[中文](README.zh-CN.md)

**Hold a modifier key, see what you can press next.**

Hold `Ctrl` for half a second. A panel appears listing every shortcut that starts with Ctrl.
Keep holding and press `Alt` as well — the list becomes the Ctrl+Alt shortcuts. Let go and it
disappears.

Nothing to set up, and it never interferes with typing.

<!-- Screenshots: the popup over an editor, and Settings | Tools | Next Key. -->

## Installation

Works with IntelliJ IDEA, PyCharm, WebStorm and the other JetBrains IDEs.

From inside the IDE: **Settings | Plugins | Marketplace**, search for *Next Key*, install and
restart.

To install a build yourself, put the jar in `<plugins>/next-key/lib/` and restart, where
`<plugins>` is the IDE's plugins directory. `build.ps1 -Install` does this for you — see
[Building](#building).

## What it shows

Shortcuts are grouped by what they do: editing, navigation, search, refactoring, running and
debugging, version control, windows. Keys are labelled the way they are on the keyboard — `[`,
`/`, `-`, `` ` ``, `↑`, `PgUp`.

The list is your own keymap. Whatever bindings you actually use are what shows up, including the
ones other plugins add. Rebind something and the panel follows.

Repeats are collapsed. An action bound to two keys takes one row, shown as `C,Ins`. The ten
bookmark shortcuts on Ctrl+0 through Ctrl+9 take one row, shown as `0-9`.

Some shortcuts need two presses. Press the first — `Ctrl+Num *` for code folding, say — and every
key that can follow appears immediately. In the main list those keys are marked `→ 5 more`, so it
is clear which ones lead somewhere.

The panel stays out of the way: press a key that leads nowhere and it goes; move the mouse while
it is still counting down and it never appears. Ctrl+wheel zooming and Ctrl+hover inspection do
not bring it up.

## Making it yours

Out of the box the panel lists the shortcuts most people use. Everything else, and every detail of
how it looks, is in **Settings | Tools | Next Key**:

- show or hide any shortcut in the keymap
- rename one to whatever you call it
- move it to a different category, or invent your own
- change how long a modifier has to be held before the panel appears
- make the panel more transparent

The same settings live in a plain text file, if editing that is easier.

## The config file

The settings screen and the config file are the same thing — whichever is more convenient.

The file is `next-key.conf`, in the IDE config directory (on Windows,
`%APPDATA%\JetBrains\<IDE><version>\`). It is written from the active keymap the first time the
plugin runs, and grouped by modifier:

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

Four forms per line:

| Line | Effect |
|---|---|
| `EditorDuplicate` | show it, under the name the IDE gives it |
| `-EditorDuplicate` | hide it |
| `EditorDuplicate = Clone line` | show it under a name of your own |
| `EditorDuplicate = Clone line \| Editing` | and put it in a category |

Leave the name out as `EditorDuplicate = | Editing` to change only the category. Anything after
`#` is a comment, and the comment on every line already carries that shortcut and the original
action name, so there is no need to look an action id up anywhere.

Categories are written in English (`Editing`, `Navigation`, and so on) and translated for display.
Any other text becomes a category of its own.

Changes take effect the next time the panel appears. No restart.

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

- Rebinding a shortcut within the same keymap needs an IDE restart before the panel reflects it.
  Switching to a different keymap is picked up on its own.
- Holding Shift alone also brings the panel up. Few shortcuts are bound to Shift alone, and while
  typing the letter key that follows cancels the countdown, so this rarely comes up in practice.
- The panel hides itself after ten seconds, in case a modifier release goes missing when focus
  moves to another application.
- The actions shown by default are the ones built into the platform. Actions that come from
  IDE-specific plugins start out hidden and can be switched on individually.
- Shortcuts added by a newly installed plugin do not appear in an existing config file. Delete the
  file and restart to have it regenerated.
- Transparency and rounded corners depend on window compositing. Over remote desktop and under
  some Linux compositors the panel stays opaque with square corners.
- Columns can end up uneven when one category is too tall to sit alongside the others.

## Uninstalling

**Settings | Plugins**, find Next Key, uninstall and restart. For a manually installed build,
delete the `next-key` directory from the IDE's plugins directory instead.

Settings live in `next-key.conf` in the IDE config directory and are left behind; delete that file
too for a clean slate.

## Contributing

Bug reports and feature requests go to
[Issues](https://github.com/CookPiu/next-key/issues). Pull requests are welcome — `build.ps1`
gets a working build without any Gradle setup, and `tools/RenderTest.java` renders the panel
offline so layout changes can be checked without launching an IDE.

## License

[MIT](LICENSE)
