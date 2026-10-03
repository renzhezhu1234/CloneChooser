package io.github.clonechooser;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import dalvik.system.PathClassLoader;

/** Verifies the actual on-device services.jar, not an AOSP source approximation. */
public final class HookContractTest {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = new PathClassLoader("/system/framework/services.jar", ClassLoader.getSystemClassLoader());
        Class<?> clazz = Class.forName("com.android.server.wm.ActivityTaskManagerService", false, loader);
        int matches = 0;
        for (Method m : clazz.getDeclaredMethods()) {
            if (!HookContract.isSystemRoute(m)) continue;
            matches++;
            System.out.println("MATCH " + Modifier.toString(m.getModifiers()) + " " + m.getName() + " parameterCount=" + m.getParameterTypes().length);
        }
        if (matches != 1) throw new AssertionError("Expected exactly one system route, found " + matches);
        System.out.println("PASS exact on-device system hook contract");
        System.exit(0);
    }
}
