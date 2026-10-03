package io.github.clonechooser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.os.UserManager;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Module implements IXposedHookLoadPackage {
    private static WeakReference<Activity> resumed = new WeakReference<>(null);
    private static final ThreadLocal<Boolean> replay = new ThreadLocal<>();
    private static boolean warned;

    private static void log(String s) {
        android.util.Log.i("CloneChooser", s);
        XposedBridge.log("CloneChooser: " + s);
    }
    private static Bundle readConfig(Context c) {
        Bundle result = c.getContentResolver().call(Uri.parse("content://" + Config.AUTHORITY), "config", null, null);
        if (result == null) throw new IllegalStateException("Configuration provider unavailable");
        return result;
    }
    private static boolean allowed(Bundle b, String source) {
        if (b == null || !b.getBoolean("enabled")) return false;
        String[] sources = b.getStringArray("sources");
        return sources != null && Arrays.asList(sources).contains(source);
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        try {
            if ("android".equals(lp.packageName) && "android".equals(lp.processName)) {
                installSystem(lp.classLoader);
            } else if (!Config.ID.equals(lp.packageName) && !"android".equals(lp.packageName)
                    && lp.isFirstApplication && android.os.Process.myUid() >= 10000
                    && android.os.Process.myUid() < 100000) {
                installClient(lp.packageName);
            }
        } catch (Throwable t) { log("hook unavailable: " + t.getClass().getSimpleName()); }
    }

    private static void installClient(String source) {
        XposedBridge.hookAllMethods(Instrumentation.class, "callActivityOnResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.args.length > 0 && p.args[0] instanceof Activity) resumed = new WeakReference<>((Activity) p.args[0]);
            }
        });
        int count = 0;
        for (Method m : Instrumentation.class.getDeclaredMethods()) {
            Class<?>[] types = m.getParameterTypes();
            if (!m.getName().equals("execStartActivity") || types.length != 7
                    || types[0] != Context.class || types[4] != Intent.class || types[5] != int.class
                    || types[6] != Bundle.class) continue;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (Boolean.TRUE.equals(replay.get())) return;
                    try {
                        Intent original = (Intent) p.args[4];
                        if (original == null || RouteIntents.hasReservedExtras(original) || (Integer) p.args[5] >= 0
                                || (original.getFlags() & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0) return;
                        if (original.getComponent() != null && Config.BRIDGE.equals(original.getComponent().getClassName())) return;
                        Context context = (Context) p.args[0];
                        // Explicit starts don't require package visibility. HMA may hide an app
                        // from PackageManager while still permitting its explicit Activity launch.
                        ComponentName destination = original.getComponent();
                        if (destination == null) {
                            ResolveInfo resolved = context.getPackageManager().resolveActivity(original, PackageManager.MATCH_DEFAULT_ONLY);
                            if (resolved == null || resolved.activityInfo == null) return;
                            destination = new ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name);
                        }
                        if (destination.getPackageName().equals(source) || destination.getPackageName().equals("android")
                                || destination.getPackageName().equals(Config.ID) || destination.getClassName().contains("ResolverActivity")
                                || destination.getClassName().contains("ChooserActivity")) return;
                        Activity activity = findActivity(context);
                        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                        Object[] args = p.args.clone();
                        args[4] = new Intent(original);
                        if (args[6] != null) args[6] = new Bundle((Bundle) args[6]);
                        Method method = (Method) p.method;
                        Object receiver = p.thisObject;
                        final ComponentName resolvedTarget = destination;
                        String foundLabel = destination.getPackageName();
                        try { foundLabel = String.valueOf(context.getPackageManager().getApplicationLabel(
                                context.getPackageManager().getApplicationInfo(destination.getPackageName(), 0))); }
                        catch (Throwable ignored) { /* A hidden package can still be a valid destination. */ }
                        final String label = foundLabel;
                        log("checking source=" + source + " target=" + destination.getPackageName());
                        // Returning null matches normal fire-and-forget Instrumentation behavior.
                        p.setResult(null);
                        new Handler(Looper.getMainLooper()).post(() -> probe(activity, method, receiver, args, resolvedTarget, label));
                    } catch (Throwable t) {
                        // Ordinary launches keep working if configuration/visibility isn't available.
                        log("client bypass: " + t.getClass().getSimpleName());
                    }
                }
            });
            count++;
        }
        log("client hooks=" + count + " source=" + source);
    }

    private static Activity findActivity(Context c) {
        for (int i = 0; c instanceof ContextWrapper && i < 12; i++) {
            if (c instanceof Activity) return (Activity) c;
            Context next = ((ContextWrapper) c).getBaseContext();
            if (next == c) break;
            c = next;
        }
        return resumed.get();
    }

    private static void invoke(Method method, Object receiver, Object[] args) throws Throwable {
        replay.set(true);
        try { XposedBridge.invokeOriginalMethod(method, receiver, args); }
        finally { replay.remove(); }
    }

    private static void launch(Activity activity, Method method, Object receiver, Object[] args) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try { invoke(method, receiver, args); }
        catch (Throwable t) {
            log("launch failed: " + t.getClass().getSimpleName());
            new AlertDialog.Builder(activity).setTitle("未能打开应用")
                    .setMessage("分身可能已关闭，或被其他跳转模块拦截。请确认系统框架作用域已生效，再重试。")
                    .setPositiveButton("知道了", null).show();
        }
    }

    private static Intent bridge(ComponentName target, String mode) {
        // An intentionally nonexistent component fails closed if the system hook is missing.
        return new Intent(Intent.ACTION_VIEW)
                .setComponent(new ComponentName(target.getPackageName(), Config.BRIDGE))
                .putExtra(Config.ORIGINAL, target.flattenToString()).putExtra(Config.MODE, mode);
    }

    private static void report(Context context, String status) {
        long identity = Binder.clearCallingIdentity();
        try {
            Bundle b = new Bundle(); b.putString("status", status);
            context.getContentResolver().call(Uri.parse("content://" + Config.AUTHORITY), "report", null, b);
        } catch (Throwable ignored) { /* Diagnostics must never stop a launch. */ }
        finally { Binder.restoreCallingIdentity(identity); }
    }

    private static void probe(Activity activity, Method method, Object receiver, Object[] originalArgs,
                              ComponentName target, String label) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean completed = new AtomicBoolean(false);
        Runnable fallback = () -> {
            if (!completed.compareAndSet(false, true)) return;
            if (!warned) {
                warned = true;
                Toast.makeText(activity, "分身检测未就绪，已按原方式打开。请检查系统框架作用域并重启。", Toast.LENGTH_LONG).show();
            }
            launch(activity, method, receiver, originalArgs);
        };
        ResultReceiver callback = new ResultReceiver(handler) {
            @Override protected void onReceiveResult(int code, Bundle data) {
                if (!completed.compareAndSet(false, true)) return;
                handler.removeCallbacks(fallback);
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (code != 0 || data == null) {
                    if (!warned) { warned = true; Toast.makeText(activity, "分身检测暂不可用，已按原方式打开", Toast.LENGTH_LONG).show(); }
                    launch(activity, method, receiver, originalArgs); return;
                }
                int[] users = data.getIntArray("users");
                log("probe target=" + target.getPackageName() + " clones=" + (users == null ? 0 : users.length));
                if (users == null || users.length == 0) {
                    log("no clone: direct launch target=" + target.getPackageName());
                    launch(activity, method, receiver, originalArgs); return;
                }
                String[] choices = new String[users.length + 1];
                choices[0] = "主应用";
                for (int i = 0; i < users.length; i++) choices[i + 1] = users.length == 1 ? "分身应用" : "分身应用 · 用户 " + users[i];
                new AlertDialog.Builder(activity).setTitle("打开「" + label + "」")
                        .setItems(choices, (dialog, which) -> {
                            if (which == 0) { launch(activity, method, receiver, originalArgs); return; }
                            Intent routed = RouteIntents.cloneRequest((Intent) originalArgs[4], target, users[which - 1]);
                            Object[] args = originalArgs.clone(); args[4] = routed;
                            launch(activity, method, receiver, args);
                        }).setNegativeButton("取消", null).show();
            }
        };
        Intent query = bridge(target, "probe").putExtra(Config.RECEIVER, ReceiverTransport.frameworkReceiver(callback));
        Object[] queryArgs = originalArgs.clone(); queryArgs[4] = query; queryArgs[6] = null;
        handler.postDelayed(fallback, 2500);
        try { invoke(method, receiver, queryArgs); }
        catch (Throwable t) { log("probe unavailable: " + t.getClass().getSimpleName()); handler.removeCallbacks(fallback); fallback.run(); }
    }

    private static void installSystem(ClassLoader loader) {
        Class<?> service = XposedHelpers.findClass("com.android.server.wm.ActivityTaskManagerService", loader);
        int count = 0;
        for (Method method : service.getDeclaredMethods()) {
            // Production ROM optimization may change private to public final.
            // Match the complete signature before AOSP stamps the Intent creator token.
            if (!HookContract.isSystemRoute(method)) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Intent incoming = (Intent) p.args[3];
                    if (incoming == null || incoming.getComponent() == null
                            || !Config.BRIDGE.equals(incoming.getComponent().getClassName())) return;
                    ResultReceiver callback = null;
                    Context reportContext = null;
                    String stage = "authenticate";
                    try {
                        int callerUid = Binder.getCallingUid();
                        String source = (String) p.args[1];
                        Context context = (Context) XposedHelpers.getObjectField(p.thisObject, "mContext");
                        reportContext = context;
                        // Never accept a caller-supplied UID, package identity or profile relationship.
                        if (callerUid < 10000 || callerUid >= 100000 || (Integer) p.args[11] != 0
                                || (Integer) p.args[7] >= 0 || source == null) throw new SecurityException("Unsupported origin");
                        long identity = Binder.clearCallingIdentity();
                        List<Integer> users;
                        ComponentName target;
                        String mode;
                        try {
                            if (context.getPackageManager().getPackageUid(source, 0) != callerUid)
                                throw new SecurityException("Caller mismatch");
                            stage = "read settings";
                            if (!allowed(readConfig(context), source)) throw new SecurityException("Source disabled");
                            target = RouteIntents.destination(incoming);
                            if (target.getPackageName().equals(source) || target.getPackageName().equals(Config.ID))
                                throw new SecurityException("Invalid destination");
                            mode = incoming.getStringExtra(Config.MODE);
                            stage = "discover clones";
                            users = availableClones(context, target);
                            if ("probe".equals(mode)) {
                                stage = "decode callback";
                                callback = incoming.getParcelableExtra(Config.RECEIVER);
                            }
                        } finally { Binder.restoreCallingIdentity(identity); }
                        if ("probe".equals(mode)) {
                            if (callback == null) throw new IllegalArgumentException("Missing framework ResultReceiver");
                            Bundle data = new Bundle(); int[] ids = new int[users.size()];
                            for (int i = 0; i < ids.length; i++) ids[i] = users.get(i);
                            data.putIntArray("users", ids); callback.send(0, data);
                            report(context, "0.1.4 检测：" + source + " → " + target.getPackageName() + "；可用分身=" + users);
                            p.setResult(0); return;
                        }
                        if (!"launch".equals(mode)) throw new SecurityException("Unknown operation");
                        int chosen = incoming.getIntExtra(Config.USER, -1);
                        if (!users.contains(chosen)) throw new SecurityException("Clone unavailable");
                        Intent routed = RouteIntents.restore(incoming);
                        // Preserve content URI origin when passing the original payload across users.
                        XposedHelpers.callMethod(routed, "prepareToLeaveUser", 0);
                        p.args[3] = routed; p.args[11] = chosen;
                        // Narrow exception: authenticated enabled source -> exported clone activity.
                        // Other Android component, grant and background-launch checks still run.
                        p.args[12] = false;
                        report(context, "0.1.4 分身启动请求：" + source + " → " + target.getPackageName() + "；用户=" + chosen);
                        log("route source=" + source + " target=" + target.getPackageName() + " user=" + chosen);
                    } catch (Throwable error) {
                        log("bridge rejected: " + error.getClass().getSimpleName());
                        if (reportContext != null) report(reportContext, "0.1.4 失败阶段=" + stage + "；异常="
                                + error.getClass().getSimpleName() + (error.getCause() == null ? "" : "/" + error.getCause().getClass().getSimpleName()));
                        // No fallback to the primary account after the user selected a clone.
                        p.setThrowable(new SecurityException("CloneChooser route unavailable"));
                    }
                }
            });
            count++;
        }
        log("system bridge hooks=" + count);
    }

    private static List<Integer> availableClones(Context context, ComponentName target) {
        List<Integer> result = new ArrayList<>();
        UserManager manager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        List<?> profiles = (List<?>) XposedHelpers.callMethod(manager, "getProfiles", 0);
        for (Object profile : profiles) {
            String type = (String) XposedHelpers.getObjectField(profile, "userType");
            if (!"android.os.usertype.profile.CLONE".equals(type)) continue;
            if (!(Boolean) XposedHelpers.callMethod(profile, "isEnabled")) continue;
            int id = XposedHelpers.getIntField(profile, "id");
            Object parent = XposedHelpers.callMethod(manager, "getProfileParent", id);
            if (id <= 0 || parent == null || XposedHelpers.getIntField(parent, "id") != 0) continue;
            UserHandle user = (UserHandle) XposedHelpers.callStaticMethod(UserHandle.class, "of", id);
            if (!manager.isUserRunning(user) || !manager.isUserUnlocked(user) || manager.isQuietModeEnabled(user)) continue;
            Context cloneContext = (Context) XposedHelpers.callMethod(context, "createContextAsUser", user, 0);
            try {
                ActivityInfo info = cloneContext.getPackageManager().getActivityInfo(target, 0);
                if (info.exported && info.enabled && info.applicationInfo.enabled
                        && (info.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SUSPENDED) == 0
                        && info.permission == null) result.add(id);
            } catch (PackageManager.NameNotFoundException ignored) { /* No target in this clone. */ }
        }
        return result;
    }
}
