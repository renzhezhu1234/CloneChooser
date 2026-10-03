package io.github.clonechooser;

import android.content.Intent;
import java.lang.reflect.Method;

/** Access modifiers can change after ROM optimization; bind by the full route shape. */
final class HookContract {
    static boolean isSystemRoute(Method method) {
        Class<?>[] t = method.getParameterTypes();
        return method.getName().equals("startActivityAsUser") && method.getReturnType() == int.class
                && t.length == 13 && t[0].getName().equals("android.app.IApplicationThread")
                && t[1] == String.class && t[2] == String.class && t[3] == Intent.class
                && t[4] == String.class && t[5].getName().equals("android.os.IBinder")
                && t[6] == String.class && t[7] == int.class && t[8] == int.class
                && t[9].getName().equals("android.app.ProfilerInfo")
                && t[10] == android.os.Bundle.class && t[11] == int.class && t[12] == boolean.class;
    }
}
