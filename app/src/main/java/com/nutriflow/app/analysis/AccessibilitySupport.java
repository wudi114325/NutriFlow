package com.nutriflow.app.analysis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import com.nutriflow.app.MealAccessibilityService;

/** User-controlled permission setup, including Android's sideload restrictions. */
public final class AccessibilitySupport {
    private AccessibilitySupport() { }

    public static boolean isEnabled(Activity activity) {
        try {
            if (Settings.Secure.getInt(activity.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false;
            String enabled = Settings.Secure.getString(activity.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            ComponentName expected = new ComponentName(activity, MealAccessibilityService.class);
            for (String value : enabled.split(":")) {
                if (expected.equals(ComponentName.unflattenFromString(value))) return true;
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    public static void openSettings(Activity activity) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                activity.startActivity(new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                        .putExtra(Intent.EXTRA_COMPONENT_NAME,
                                new ComponentName(activity, MealAccessibilityService.class)));
                return;
            } catch (RuntimeException ignored) { }
        }
        try { activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch (RuntimeException ignored) {
            Toast.makeText(activity, "请在手机设置中搜索“无障碍”，选择“营养流跨应用分析”", Toast.LENGTH_LONG).show();
        }
    }

    public static void showHelp(Activity activity) {
        new AlertDialog.Builder(activity).setTitle("跨应用分析开启帮助")
                .setMessage("1. 在系统无障碍设置中开启“营养流跨应用分析”。\n\n"
                        + "2. 如果开关灰色或提示“受限设置”：部分 Android 13 及以上手机需要先打开营养流的应用信息，在右上角菜单允许受限设置，再返回开启。不同品牌入口可能不同。\n\n"
                        + "3. 如果已开启却没有反应：先关闭再开启该系统开关，回到营养流重新打开外卖 App。\n\n"
                        + "4. 点击外卖页面左上角开关开启识别，再进入具体菜品；长按开关可重试识别。")
                .setPositiveButton("无障碍设置", (dialog, which) -> openSettings(activity))
                .setNeutralButton("应用信息", (dialog, which) -> {
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + activity.getPackageName())));
                    } catch (RuntimeException ignored) {
                        Toast.makeText(activity, "请长按营养流桌面图标，打开应用信息", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("关闭", null).show();
    }
}
