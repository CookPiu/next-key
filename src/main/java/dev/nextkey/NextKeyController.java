package dev.nextkey;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * When the popup shows up:
 * <ul>
 *   <li>hold any modifier combination for the configured delay and the popup lists every key
 *       that combination leads to;</li>
 *   <li>while it is up, adding or dropping a modifier (Ctrl to Ctrl+Alt to Ctrl+Alt+Shift)
 *       swaps in the new list immediately, without waiting again;</li>
 *   <li>pressing the first stroke of a two-stroke shortcut (Ctrl+K, say) lists every second
 *       stroke right away;</li>
 *   <li>releasing every modifier, pressing a key that is not a prefix, or moving the mouse
 *       while waiting all dismiss it.</li>
 * </ul>
 * Keys are watched through {@link KeyboardFocusManager} rather than the platform's internal
 * IdeEventQueue, so this depends on public JDK API only. The dispatcher always returns false
 * and never swallows a key.
 * <p>
 * Registered as an application service so the platform calls {@link #dispose()} when the plugin
 * is disabled or unloaded, which is where the listeners come back off.
 */
public final class NextKeyController implements KeyEventDispatcher, AWTEventListener, Disposable {

    /**
     * Fallback if the popup somehow stays up: on focus loss the modifier's KEY_RELEASED may
     * never arrive.
     */
    private static final int AUTO_HIDE_MS = 10_000;

    private static final Logger LOG = Logger.getInstance(NextKeyController.class);

    private final HintWindow hint = new HintWindow();
    private final Timer showTimer;
    private final Timer autoHideTimer;

    /**
     * Hold on to the KeyboardFocusManager used at install time: getCurrentKeyboardFocusManager()
     * returns the instance for the calling thread's AppContext, and teardown may not run on the
     * same thread.
     */
    private KeyboardFocusManager focusManager;
    private boolean installed;

    private int pendingModifiers;
    /** While waiting for the second stroke of a two-stroke shortcut, ignore modifier changes. */
    private boolean waitingSecond;

    /** Instantiated reflectively by the platform as an application service. */
    public NextKeyController() {
        showTimer = new Timer(HintSettings.DEFAULT_DELAY_MS, e -> showModifiers(pendingModifiers));
        showTimer.setRepeats(false);
        autoHideTimer = new Timer(AUTO_HIDE_MS, e -> reset());
        autoHideTimer.setRepeats(false);
    }

    void install() {
        SwingUtilities.invokeLater(() -> {
            if (installed) {
                return;
            }
            installed = true;
            focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            focusManager.addKeyEventDispatcher(this);
            Toolkit.getDefaultToolkit().addAWTEventListener(this,
                    AWTEvent.MOUSE_MOTION_EVENT_MASK
                            | AWTEvent.MOUSE_WHEEL_EVENT_MASK
                            | AWTEvent.MOUSE_EVENT_MASK);
        });

        // Building the index walks every action in the keymap to read its display name, which
        // takes a few hundred milliseconds. Leaving that to the first keypress blocks the EDT:
        // by the time the index is ready and the popup appears, the queued KEY_RELEASED closes
        // it again, so the first press looks like it did nothing.
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ShortcutIndex.get();
            } catch (Throwable t) {
                LOG.warn("[next-key] index warm-up failed, will build on first use", t);
            }
        });
    }

    @Override
    public void dispose() {
        installed = false;
        if (focusManager != null) {
            focusManager.removeKeyEventDispatcher(this);
            focusManager = null;
        }
        try {
            Toolkit.getDefaultToolkit().removeAWTEventListener(this);
        } catch (Throwable ignored) {
            // The Toolkit may already be gone while the IDE shuts down
        }
        showTimer.stop();
        autoHideTimer.stop();
        hint.dispose();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        try {
            handle(e);
        } catch (Throwable ignored) {
            // A hint must never disturb typing, so anything unexpected is simply dropped
        }
        return false;
    }

    private void handle(KeyEvent e) {
        int id = e.getID();
        int code = e.getKeyCode();
        int mods = e.getModifiersEx() & ShortcutIndex.MODIFIER_MASK;

        if (id == KeyEvent.KEY_PRESSED) {
            if (ShortcutIndex.isModifierKey(code)) {
                if (waitingSecond) {
                    // The user is assembling the modifiers of the second stroke; leave it alone
                    return;
                }
                mods |= ShortcutIndex.maskOf(code);
                if (hint.isShowing()) {
                    showModifiers(mods);
                } else if (mods != 0 && (!showTimer.isRunning() || mods != pendingModifiers)) {
                    // Holding a key down makes the OS repeat KEY_PRESSED, and restarting the
                    // timer on every repeat would keep pushing the deadline back: the first
                    // repeat lands around 500 ms in, the rest about every 33 ms, so any delay
                    // at or above that never fires. Only a genuine change of modifiers restarts
                    // the countdown.
                    pendingModifiers = mods;
                    showTimer.setInitialDelay(ShortcutIndex.settings().getDelayMs());
                    showTimer.restart();
                }
                return;
            }

            if (waitingSecond) {
                // The second stroke is in; the hint has done its job
                reset();
                return;
            }

            // Without a modifier this cannot be the first stroke of a two-stroke shortcut.
            // The short-circuit matters: otherwise every character typed would query the index,
            // and the index checks the config file's timestamp.
            if (mods == 0) {
                if (showTimer.isRunning() || hint.isShowing()) {
                    reset();
                }
                return;
            }

            List<ShortcutIndex.Group> second =
                    ShortcutIndex.get().bySecondPrefix.get(ShortcutIndex.prefixKey(mods, code));
            if (second != null && !second.isEmpty()) {
                showSecond(mods, code, second);
            } else {
                reset();
            }
            return;
        }

        if (id == KeyEvent.KEY_RELEASED && ShortcutIndex.isModifierKey(code)) {
            if (waitingSecond) {
                return;
            }
            mods &= ~ShortcutIndex.maskOf(code);
            if (mods == 0) {
                reset();
            } else if (hint.isShowing()) {
                showModifiers(mods);
            } else if (showTimer.isRunning()) {
                pendingModifiers = mods;
            }
        }
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        // Only suppress while waiting: Ctrl+wheel to zoom and Ctrl+hover to inspect a type
        // should not raise a hint. Once the popup is up the mouse is ignored, otherwise the
        // slightest movement would take it away while it is being read.
        if (showTimer.isRunning()) {
            showTimer.stop();
        }
    }

    private void showModifiers(int modifiers) {
        if (modifiers == 0) {
            reset();
            return;
        }
        List<ShortcutIndex.Group> groups = ShortcutIndex.get().byModifiers.get(modifiers);
        if (groups == null || ShortcutIndex.count(groups) == 0) {
            // Nothing bound to this combination: take the popup down but keep listening
            hint.hide();
            return;
        }
        waitingSecond = false;
        hint.show(activeWindow(),
                NextKeyBundle.message("hint.title.modifiers", ShortcutIndex.modifiersLabel(modifiers)),
                NextKeyBundle.message("hint.subtitle.count", ShortcutIndex.count(groups)),
                groups, ShortcutIndex.settings().getOpacity());
        autoHideTimer.restart();
    }

    private void showSecond(int modifiers, int keyCode, List<ShortcutIndex.Group> groups) {
        showTimer.stop();
        waitingSecond = true;
        String prefix = ShortcutIndex.modifiersPrefix(modifiers) + ShortcutIndex.keyText(keyCode);
        hint.show(activeWindow(),
                NextKeyBundle.message("hint.title.second", prefix),
                NextKeyBundle.message("hint.subtitle.count", ShortcutIndex.count(groups)),
                groups, ShortcutIndex.settings().getOpacity());
        autoHideTimer.restart();
    }

    private void reset() {
        showTimer.stop();
        autoHideTimer.stop();
        waitingSecond = false;
        hint.hide();
    }

    private static Window activeWindow() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }
}
