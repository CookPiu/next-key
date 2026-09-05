package dev.nextkey;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

import java.util.List;

/**
 * Plugin entry point. Once the IDE frame exists, pull the controller out of the application
 * service container and let it install the global key listener. Removing it again is the
 * platform's job, by disposing the service.
 */
public final class NextKeyListener implements AppLifecycleListener {

    @Override
    public void appFrameCreated(List<String> commandLineArgs) {
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
