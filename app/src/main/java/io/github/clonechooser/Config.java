package io.github.clonechooser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class Config {
    static final String ID = "io.github.clonechooser";
    static final String AUTHORITY = ID + ".config";
    static final String BRIDGE = ID + ".InternalRoute";
    static final String ORIGINAL = ID + ".original";
    static final String RECEIVER = ID + ".receiver";
    static final String MODE = ID + ".mode";
    static final String USER = ID + ".user";
    static final String PROTOCOL = ID + ".protocol";
    static final int PROTOCOL_VERSION = 2;
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("config", Context.MODE_PRIVATE); }
    static Set<String> sources(Context c) {
        return new HashSet<>(prefs(c).getStringSet("sources", new HashSet<>(Arrays.asList(
                "com.smzdm.client.android", "com.tencent.mm", "com.xunmeng.pinduoduo"))));
    }
    static Bundle snapshot(Context c) {
        Bundle b = new Bundle();
        b.putBoolean("enabled", prefs(c).getBoolean("enabled", true));
        b.putStringArray("sources", sources(c).toArray(new String[0]));
        return b;
    }
}
