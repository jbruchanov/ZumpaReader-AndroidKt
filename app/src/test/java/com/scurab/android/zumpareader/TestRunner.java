package com.scurab.android.zumpareader;

import android.os.Build;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Created by JBruchanov on 24/07/2015.
 * https://github.com/robolectric/robolectric/wiki/2.4-to-3.0-Upgrade-Guide
 * https://philio.me/android-data-binding-with-robolectric-3/
 */
public class TestRunner extends RobolectricGradleTestRunner {
    public TestRunner(Class<?> klass) throws InitializationError {
        super(klass);
        //BusProvider.unregisterAll();//release any forgotten registered objects
    }

    @Override
    public Config getConfig(Method method) {
        Config config = super.getConfig(method);
        if (config.constants() == Void.class) {
            config = ensureSpy(config);
            doReturn(BuildConfig.class).when(config).constants();
        }

        if (config.sdk() == null || config.sdk().length == 0) {
            config = ensureSpy(config);
            doReturn(new int[]{Build.VERSION_CODES.LOLLIPOP}).when(config).sdk();
            doReturn(ZumpaReaderApp.class).when(config).application();
        }
        return config;
    }

    private static Config ensureSpy(Config config) {
        if (!config.getClass().getName().contains("Mockito")) {
            config = spy(config);
        }
        return config;
    }
}
