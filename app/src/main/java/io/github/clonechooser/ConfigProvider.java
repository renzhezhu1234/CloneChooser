package io.github.clonechooser;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import java.util.Set;

/** Read-only configuration. Other apps cannot edit or enable their own access. */
public final class ConfigProvider extends ContentProvider {
    @Override public void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        writer.println("CloneChooser diagnostics");
        writer.println(Config.prefs(getContext()).getString("diagnostics", "No system diagnostics recorded"));
        writer.println("enabled=" + Config.prefs(getContext()).getBoolean("enabled", true));
        writer.println("sources=" + Config.sources(getContext()));
    }
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if ("report".equals(method)) {
            if (uid != Process.SYSTEM_UID) throw new SecurityException("System only");
            if (extras != null) {
                String status = extras.getString("status", "");
                if (status.length() > 400) status = status.substring(0, 400);
                String previous = Config.prefs(getContext()).getString("diagnostics", "");
                String next = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date())
                        + " " + status + "\n" + previous;
                Config.prefs(getContext()).edit().putString("diagnostics", next.substring(0, Math.min(6000, next.length()))).apply();
            }
            return Bundle.EMPTY;
        }
        if (!"config".equals(method)) throw new IllegalArgumentException("Unknown method");
        if (uid != Process.SYSTEM_UID && uid != Process.myUid()) {
            boolean allowed = false;
            Set<String> sources = Config.sources(getContext());
            String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
            if (uid / 100000 == 0 && packages != null) {
                for (String p : packages) if (sources.contains(p)) allowed = true;
            }
            if (!allowed) throw new SecurityException("Source not enabled");
        }
        return Config.snapshot(getContext());
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
