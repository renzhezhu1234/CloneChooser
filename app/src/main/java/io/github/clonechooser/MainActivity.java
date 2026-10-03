package io.github.clonechooser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

public final class MainActivity extends Activity {
    private LinearLayout body;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        body.setPadding(p, p, p, p);
        scroll.addView(body);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        scroll.requestApplyInsets();
        render();
    }
    private void text(String s, float size) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setPadding(0, 12, 0, 20); body.addView(t);
    }
    private void button(String title, View.OnClickListener click) {
        Button b = new Button(this); b.setText(title); b.setOnClickListener(click); body.addView(b);
    }
    private void render() {
        body.removeAllViews();
        text("分身跳转选择", 27);
        text("有分身才选择，没有分身直接打开", 17);
        Switch enabled = new Switch(this);
        enabled.setText("启用分身跳转选择");
        enabled.setChecked(Config.prefs(this).getBoolean("enabled", true));
        enabled.setOnCheckedChangeListener((b, on) -> Config.prefs(this).edit().putBoolean("enabled", on).apply());
        body.addView(enabled);
        text("已允许 " + Config.sources(this).size() + " 个来源应用。目标应用不限；仅检测到可用分身时弹出菜单。", 16);
        button("选择来源应用", v -> selectSources());
        button("查看检测记录", v -> new AlertDialog.Builder(this).setTitle("最近检测记录")
                .setMessage(Config.prefs(this).getString("diagnostics", "尚无系统端检测记录。请启用系统框架及来源作用域，更新模块后重启，再触发一次跨应用跳转。"))
                .setPositiveButton("关闭", null).show());
        button("预览跳转菜单", v -> new AlertDialog.Builder(this)
                .setTitle("选择打开方式 · 示例")
                .setItems(new String[]{"主应用", "分身应用（实际使用时自动检测）"}, (d, w) -> {})
                .setNegativeButton("取消", null).show());
        text("启用方法\n\n1. 在 LSPosed 中启用本模块。\n2. 勾选「系统框架」以及需要发起跳转的应用。\n3. 在这里允许相同的来源应用。默认已允许微信、什么值得买。\n4. 首次启用系统框架作用域后重启手机。来源作用域变化后，结束该应用进程再打开。", 16);
        text("有可用分身：菜单列出主应用和原生分身。\n没有可用分身：直接按原方式打开目标应用。\n工作资料和其他独立用户不会混入菜单。\n\n当前版本处理常规 startActivity 跨应用跳转，包括可解析的网页深链。应用内部页面、系统选择器、要求返回结果的跳转、PendingIntent 和批量启动不在此版本的拦截范围。", 14);
        text("设置由系统端读取，来源应用无需查询本模块。如使用其他跳转拦截模块，请避免抢先拦截同一跳转。\n\nv0.1.4 · 无联网权限 · 不记录链接、商品参数或账号数据", 14);
    }
    private void selectSources() {
        PackageManager pm = getPackageManager();
        TreeMap<String, ApplicationInfo> found = new TreeMap<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(launcher, 0)) {
            ApplicationInfo a = r.activityInfo.applicationInfo;
            if (!a.packageName.equals(getPackageName()) && a.uid >= 10000) found.put(a.packageName, a);
        }
        List<ApplicationInfo> apps = new ArrayList<>(found.values());
        apps.sort(Comparator.comparing(a -> pm.getApplicationLabel(a).toString()));
        Set<String> selected = Config.sources(this);
        String[] labels = new String[apps.size()]; boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            ApplicationInfo a = apps.get(i);
            labels[i] = pm.getApplicationLabel(a) + "\n" + a.packageName;
            checked[i] = selected.contains(a.packageName);
        }
        new AlertDialog.Builder(this).setTitle("允许哪些应用显示跳转菜单")
                .setMultiChoiceItems(labels, checked, (d, which, on) -> {
                    if (on) selected.add(apps.get(which).packageName);
                    else selected.remove(apps.get(which).packageName);
                })
                .setPositiveButton("保存", (d, w) -> {
                    Config.prefs(this).edit().putStringSet("sources", new HashSet<>(selected)).apply(); render();
                }).setNegativeButton("取消", null).show();
    }
}
