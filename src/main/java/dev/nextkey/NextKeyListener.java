package dev.nextkey;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

import java.util.List;

/**
 * Plugin entry point, listening on two occasions.
 * <p>
 * {@link #appFrameCreated} covers the normal case: the IDE starts with the plugin already
 * enabled. {@link #pluginLoaded} covers being switched on from Settings | Plugins while the IDE
 * is running — that fires no lifecycle event, so without it the plugin would sit there enabled
 * but with no key listener attached until the next restart.
 * <p>
 * Taking the listeners back off is the platform's job, by disposing {@link NextKeyController}.
 */
public final class NextKeyListener implements AppLifecycleListener, DynamicPluginListener {

    private static final String PLUGIN_ID = "io.github.cookpiu.nextkey";

    @Override
    public void appFrameCreated(List<String> commandLineArgs) {
        install();
    }

    @Override
    public void pluginLoaded(IdeaPluginDescriptor pluginDescriptor) {
        // This fires for every plugin that gets loaded, so check it is actually ours
        if (pluginDescriptor != null
                && pluginDescriptor.getPluginId() != null
                && PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString())) {
            install();
        }
    }

    /** Idempotent: the controller ignores a second install. */
    private static void install() {
        Application application = ApplicationManager.getApplication();
        if (application == null) {
            return;
        }
        NextKeyController controller = application.getService(NextKeyController.class);
        if (controller != null) {
            controller.install();
        }
    }
}
