package io.github.clonechooser;

import android.content.ComponentName;
import android.content.Intent;

/** Intent transformations shared by the client, bridge and device payload tests. */
final class RouteIntents {
    static boolean hasReservedExtras(Intent intent) {
        return intent.hasExtra(Config.ORIGINAL) || intent.hasExtra(Config.MODE)
                || intent.hasExtra(Config.USER) || intent.hasExtra(Config.RECEIVER) || intent.hasExtra(Config.PROTOCOL);
    }
    static Intent cloneRequest(Intent original, ComponentName target, int user) {
        if (hasReservedExtras(original)) throw new IllegalArgumentException("Reserved extras");
        Intent result = new Intent(original);
        result.setSelector(null);
        result.setComponent(new ComponentName(target.getPackageName(), Config.BRIDGE));
        result.putExtra(Config.ORIGINAL, target.flattenToString());
        result.putExtra(Config.MODE, "launch");
        result.putExtra(Config.USER, user);
        result.putExtra(Config.PROTOCOL, Config.PROTOCOL_VERSION);
        return result;
    }
    static ComponentName destination(Intent request) {
        ComponentName target = ComponentName.unflattenFromString(request.getStringExtra(Config.ORIGINAL));
        if (target == null || request.getComponent() == null
                || !Config.BRIDGE.equals(request.getComponent().getClassName())
                || !target.getPackageName().equals(request.getComponent().getPackageName())
                || Config.BRIDGE.equals(target.getClassName())) throw new SecurityException("Invalid destination");
        return target;
    }
    static Intent restore(Intent request) {
        ComponentName target = destination(request);
        Intent result = new Intent(request);
        result.removeExtra(Config.ORIGINAL); result.removeExtra(Config.MODE);
        result.removeExtra(Config.RECEIVER); result.removeExtra(Config.USER);
        result.removeExtra(Config.PROTOCOL);
        result.setComponent(target);
        result.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return result;
    }
}
