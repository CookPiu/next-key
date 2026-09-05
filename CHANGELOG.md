# Changelog

Entries here are mirrored into `<change-notes>` in `plugin.xml`, which is what the Marketplace
page displays.

## 0.4.0

- The hold delay is configurable, 100-2000 ms (`delay-ms` in the config file, a spinner in the
  settings UI)
- Adjustable popup opacity (`opacity` in the config file, a slider in the settings UI)
- Key names now match the keyboard: `[`, `/`, `PgUp`, `↑`, `Num *` instead of Swing's
  `Open Bracket`, `Slash`, `Page Up`, `Up`, `Multiply`. They no longer follow the OS
  locale either — `getKeyText` returns translated names on a non-English system
- Keys are drawn as keycaps; the popup has rounded corners
- Long action names are truncated so one entry cannot widen a whole column
- Interface language follows the IDE language setting instead of the OS locale, and
  bundle loading no longer falls back to the JVM default locale
- The shortcut index is built in the background at startup, so the first press no longer
  blocks on it
- Enabling the plugin from Settings | Plugins takes effect immediately. Only
  `AppLifecycleListener` was wired up before, which fires at IDE startup and not when a plugin
  is switched on, leaving it enabled but inert until the next restart
- Settings UI layout fixes: the opacity slider no longer swallows its own value label,
  columns keep readable widths, the footer note wraps instead of being clipped

## 0.3.0

- Entries are grouped by category (Editing, Navigation, Search, Refactor, Run & Debug,
  Version Control, Windows & Tools, Other)
- Settings UI at `Settings | Tools | Next Key` for showing, renaming and re-categorizing
  individual shortcuts
- English and Simplified Chinese interface
- Duplicate entries are folded: one action bound to several keys becomes `C,Ins`,
  actions differing only by a trailing number become `0-9`
- Listeners are now removed when the plugin is disabled or unloaded
- No file system access on the typing hot path

## 0.2.0

- Works with any modifier combination, not just Ctrl
- Modifier changes update the panel in place (Ctrl → Ctrl+Alt → Ctrl+Alt+Shift)
- Two-stroke shortcuts: pressing the first stroke lists every second stroke
- Only common actions are shown by default; the rest can be enabled in `next-key.conf`

## 0.1.0

- Hold Ctrl for half a second to list every Ctrl shortcut in the active keymap
