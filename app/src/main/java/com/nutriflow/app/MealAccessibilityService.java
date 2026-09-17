package com.nutriflow.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.nutriflow.app.analysis.DetailVisitTracker;
import com.nutriflow.app.analysis.DeliveryLaunch;
import com.nutriflow.app.analysis.FoodDetailDetector;
import com.nutriflow.app.analysis.MealNutrition;
import com.nutriflow.app.analysis.MealNutritionAnalyzer;

import java.util.Locale;

/**
 * Cross-app bridge for the supported delivery apps.
 *
 * A delivery app often reports the click on an image/container while the
 * food title is rendered in a sibling node. The service therefore waits for
 * the detail page and scans the visible accessibility tree before showing the
 * analysis sheet.
 */
public class MealAccessibilityService extends AccessibilityService {
    private static final int GREEN = Color.rgb(13, 135, 95);
    private static final int DARK = Color.rgb(23, 51, 43);
    private static final int MUTED = Color.rgb(102, 124, 115);
    private static final int ORANGE = Color.rgb(224, 116, 55);
    private static final String[] DELIVERY_PACKAGES = {
            "com.sankuai.meituan",
            "com.sankuai.meituan.takeoutnew",
            "me.ele",
            "com.taobao.taobao",
            "com.jingdong.app.mall"
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DetailVisitTracker visits = new DetailVisitTracker();
    private WindowManager windowManager;
    private View overlay;
    private TextView recognitionToggle;
    private Runnable pendingScan;
    private String sessionPackage = "";
    private String overlayKey = "";
    private boolean recognitionEnabled;
    private String recognitionStatus = "等待菜品";
    private int readFailures;
    private static final String TAG = "NutriFlowRecognition";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        recognitionEnabled = getSharedPreferences("nutriflow", MODE_PRIVATE)
                .getBoolean("recognition_enabled", false);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_SCROLLED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 120;
        // Observe foreground changes so overlays also disappear on Home/Recents.
        // Text is read only from the explicitly launched delivery app below.
        info.packageNames = null;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        try {
            setServiceInfo(info);
        } catch (RuntimeException failure) {
            // Some OEMs reject interactive-window retrieval; active-root scanning
            // can still work. Do not crash and leave a dead enabled service.
            info.flags &= ~AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            try { setServiceInfo(info); }
            catch (RuntimeException retryFailure) { logFailure("connect", retryFailure); }
        }
        sessionPackage = DeliveryLaunch.restore(this);
        if (!isDeliveryPackage(sessionPackage)) sessionPackage = "";
        if (!sessionPackage.isEmpty()) scheduleScan(350);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        if (isDeliveryPackage(pkg) && DeliveryLaunch.consume(this, pkg)) {
            hideSessionViews();
            sessionPackage = pkg;
            visits.reset();
        }
        if (sessionPackage.isEmpty()) return;
        // Our own sheet/button updates must never become meal evidence.
        if (getPackageName().equals(pkg)
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
        scheduleScan(180);
    }

    private boolean isDeliveryPackage(String pkg) {
        for (String known : DELIVERY_PACKAGES) if (known.equals(pkg)) return true;
        return false;
    }

    private void scheduleScan(long delay) {
        // Coalesce noisy content updates without indefinitely postponing a scan.
        if (pendingScan != null) return;
        pendingScan = () -> {
            pendingScan = null;
            try {
                inspectForeground();
            } catch (RuntimeException failure) {
                // Accessibility nodes/windows can become invalid between IPC calls.
                // Recover on a later frame rather than terminating the service.
                logFailure("scan", failure);
                cancelPendingScan();
                removeOverlay();
                readFailures++;
                setRecognitionStatus(readFailures <= 3 ? "读取中" : "请重试");
                if (readFailures <= 3) scheduleScan(1500);
            }
        };
        handler.postDelayed(pendingScan, delay);
    }

    private void cancelPendingScan() {
        if (pendingScan != null) handler.removeCallbacks(pendingScan);
        pendingScan = null;
    }

    private void inspectForeground() {
        android.app.KeyguardManager lock = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        android.os.PowerManager power = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if ((lock != null && lock.isKeyguardLocked()) || (power != null && !power.isInteractive())) {
            hideSessionViews(true);
            return;
        }
        AccessibilityNodeInfo root = deliveryRoot();
        if (root == null) {
            // A loading frame or touching our own overlay is not a new visit.
            hideSessionViews(false);
            if (++readFailures <= 3) scheduleScan(1000);
            return;
        }
        try {
            String pkg = root.getPackageName() == null ? "" : root.getPackageName().toString();
            if (!sessionPackage.equals(pkg)) {
                hideSessionViews(true);
                return;
            }
            showRecognitionToggle();
            if (!recognitionEnabled) { readFailures = 0; return; }
            android.graphics.Rect bounds = new android.graphics.Rect();
            root.getBoundsInScreen(bounds);
            ScanBudget budget = new ScanBudget();
            Scope scope = readScope(root, bounds, budget, 0);
            readFailures = 0;
            // A complete foreground sheet remains usable if its background menu
            // exceeded the budget. Never infer a result from a truncated scope.
            FoodDetailDetector.Result detail = scope.detail;
            if (detail == null && !scope.complete) {
                removeOverlay();
                setRecognitionStatus("读取中");
                return;
            }
            String key = detail == null ? "" : pkg + ":" + FoodDetailDetector.identity(detail.foodName);
            boolean ready = visits.observe(key, android.os.SystemClock.uptimeMillis());
            if (detail == null || (!overlayKey.isEmpty() && !overlayKey.equals(key))) removeOverlay();
            setRecognitionStatus(detail == null ? "等待菜品" : "已识别");
            if (detail != null && ready && overlay == null) {
                showOverlay(detail.foodName, detail.evidence, detail.price, pkg);
                if (overlay != null) {
                    overlayKey = key;
                    visits.markShown(key);
                } else {
                    setRecognitionStatus("请重试");
                }
            }
        } finally {
            recycle(root);
            if (recognitionToggle != null) scheduleScan(1000);
        }
    }

    private AccessibilityNodeInfo deliveryRoot() {
        // Touching TYPE_ACCESSIBILITY_OVERLAY may make it the active window on
        // some phones. Select the actual focused application, excluding our UI.
        java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows = null;
        AccessibilityNodeInfo focusedRoot = null;
        AccessibilityNodeInfo activeRoot = null;
        try {
            windows = getWindows();
            if (windows != null) for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                if (window == null || window.getType()
                        == android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
                if (!window.isFocused() && !window.isActive()) continue;
                AccessibilityNodeInfo candidate = window.getRoot();
                if (candidate == null) continue;
                if (window.isFocused()) {
                    recycle(focusedRoot);
                    focusedRoot = candidate;
                } else {
                    recycle(activeRoot);
                    activeRoot = candidate;
                }
            }
        } catch (RuntimeException failure) {
            logFailure("windows", failure);
        } finally {
            if (windows != null) for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                if (window != null) try { window.recycle(); } catch (RuntimeException ignored) { }
            }
        }
        if (focusedRoot != null) {
            recycle(activeRoot);
            // Returning the foreign focused root lets the caller hide immediately.
            return focusedRoot;
        }
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root != null && !getPackageName().contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
                recycle(activeRoot);
                return root;
            }
        } catch (RuntimeException failure) {
            logFailure("active-window", failure);
        }
        recycle(root);
        return activeRoot;
    }

    private static void recycle(AccessibilityNodeInfo node) {
        if (node != null) try { node.recycle(); } catch (RuntimeException ignored) { }
    }

    private void logFailure(String stage, RuntimeException failure) {
        // Do not retain page text, food names, or exception messages from another app.
        android.util.Log.w(TAG, stage + ": " + failure.getClass().getSimpleName());
    }

    private static final class ScanBudget {
        int nodes; int characters;
        final long deadline = android.os.SystemClock.uptimeMillis() + 250;
        boolean exhausted() {
            return nodes >= 1000 || characters >= 24000 || android.os.SystemClock.uptimeMillis() > deadline;
        }
    }
    private static final class Scope {
        final java.util.List<String> lines = new java.util.ArrayList<>();
        FoodDetailDetector.Result detail;
        boolean ambiguous;
        boolean complete = true;
    }

    private Scope readScope(AccessibilityNodeInfo node, android.graphics.Rect screen,
                            ScanBudget budget, int depth) {
        Scope result = new Scope();
        if (depth > 40 || budget.exhausted()) { result.complete = false; return result; }
        budget.nodes++;
        // Don't prune invisible/zero-sized container nodes: WebViews sometimes
        // report those for parents whose visible children contain the food title.
        android.graphics.Rect rect = new android.graphics.Rect();
        node.getBoundsInScreen(rect);
        if (node.isVisibleToUser() && android.graphics.Rect.intersects(screen, rect)) {
            addText(result.lines, node.getText(), budget);
            if (node.getContentDescription() != null
                    && !node.getContentDescription().toString().contentEquals(
                            node.getText() == null ? "" : node.getText())) {
                addText(result.lines, node.getContentDescription(), budget);
            }
            String id = node.getViewIdResourceName();
            if (id != null && node.isClickable()) {
                String localId = id.substring(id.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (localId.matches("(?:[a-z]+_)*(?:close|dismiss)(?:_[a-z]+)*")) addText(result.lines, "关闭", budget);
                else if (localId.matches("(?:[a-z]+_)*back(?:_[a-z]+)*")) addText(result.lines, "返回", budget);
            }
        }
        int count = node.getChildCount();
        java.util.List<String> childLines = new java.util.ArrayList<>();
        // Modal sheets are commonly appended after a large background menu.
        for (int i = count - 1; i >= 0; i--) {
            if (budget.exhausted()) { result.complete = false; break; }
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) { result.complete = false; continue; }
            try {
                Scope sub = readScope(child, screen, budget, depth + 1);
                childLines.addAll(0, sub.lines);
                result.complete &= sub.complete;
                if (sub.ambiguous) result.ambiguous = true;
                if (sub.detail != null) {
                    // A complete closeable foreground sheet excludes background
                    // menu evidence and avoids traversing hundreds of menu nodes.
                    if (sub.detail.dismissible) { result.detail = sub.detail; return result; }
                    if (result.detail != null && !FoodDetailDetector.identity(result.detail.foodName)
                            .equals(FoodDetailDetector.identity(sub.detail.foodName))) result.ambiguous = true;
                    result.detail = sub.detail;
                }
            } finally { recycle(child); }
        }
        result.lines.addAll(childLines);
        if (result.ambiguous) { result.detail = null; return result; }
        boolean usableBounds = rect.isEmpty() || (rect.width() >= screen.width() * 0.55f
                && rect.height() >= screen.height() * 0.20f);
        if (result.detail == null && result.complete && usableBounds && !result.lines.isEmpty()) {
            result.detail = FoodDetailDetector.detect(result.lines);
        }
        return result;
    }

    private void addText(java.util.List<String> lines, CharSequence raw, ScanBudget budget) {
        if (raw == null || raw.length() > 4000) return;
        for (String part : raw.toString().split("[\\n\\r|•]+")) {
            String text = part.trim();
            if (text.isEmpty() || text.length() > 1000) continue;
            budget.characters += text.length();
            lines.add(text);
        }
    }

    private void setRecognitionStatus(String status) {
        if (status.equals(recognitionStatus)) return;
        recognitionStatus = status;
        updateToggle();
    }

    private void showRecognitionToggle() {
        if (recognitionToggle != null || windowManager == null) return;
        // Accessibility overlays are available from API 22; do not use TYPE_PHONE
        // (which would silently require an unrelated overlay permission on API 21).
        if (Build.VERSION.SDK_INT < 22) return;
        recognitionToggle = txt("", 12, Color.WHITE);
        recognitionToggle.setGravity(Gravity.CENTER);
        recognitionToggle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        recognitionToggle.setElevation(dp(6));
        recognitionToggle.setOnClickListener(v -> {
            recognitionEnabled = !recognitionEnabled;
            getSharedPreferences("nutriflow", MODE_PRIVATE).edit()
                    .putBoolean("recognition_enabled", recognitionEnabled).apply();
            cancelPendingScan();
            removeOverlay();
            visits.reset();
            readFailures = 0;
            recognitionStatus = "等待菜品";
            updateToggle();
            scheduleScan(180);
        });
        recognitionToggle.setOnLongClickListener(v -> {
            if (!recognitionEnabled) return false;
            removeOverlay();
            visits.reset();
            readFailures = 0;
            setRecognitionStatus("读取中");
            cancelPendingScan();
            scheduleScan(180);
            return true;
        });
        updateToggle();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(dp(112), dp(48),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(10);
        params.y = dp(56);
        try { windowManager.addView(recognitionToggle, params); }
        catch (RuntimeException ignored) { recognitionToggle = null; }
    }

    private void updateToggle() {
        if (recognitionToggle == null) return;
        recognitionToggle.setText("◉ 营养流\n" + (recognitionEnabled ? "已开·" + recognitionStatus : "识别已关"));
        recognitionToggle.setBackground(rounded(recognitionEnabled ? GREEN : MUTED, 16));
        recognitionToggle.setContentDescription(recognitionEnabled
                ? "营养流识别已开启，" + recognitionStatus + "，点击关闭，长按重新识别" : "营养流识别已关闭，点击开启");
    }

    private void hideSessionViews() { hideSessionViews(true); }

    private void hideSessionViews(boolean resetVisit) {
        cancelPendingScan();
        removeOverlay();
        if (resetVisit) visits.reset();
        if (recognitionToggle != null && windowManager != null) {
            try { windowManager.removeView(recognitionToggle); } catch (RuntimeException ignored) { }
            recognitionToggle = null;
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView txt(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private Button action(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(color, 12));
        b.setMinHeight(dp(44));
        return b;
    }

    private void showOverlay(final String rawText, final String pageText,
                             final double price, final String packageName) {
        removeOverlay();

        android.content.SharedPreferences preferences =
                getSharedPreferences("nutriflow", MODE_PRIVATE);
        String allergy = preferences.getString("allergy", "无");
        String taste = preferences.getString("taste", "未填写");
        String budgetText = preferences.getString("budget", "25");
        int portionGrams = readPortionGrams(preferences);
        MealNutritionAnalyzer.Result analysis = MealNutritionAnalyzer.analyze(
                rawText, pageText, price, portionGrams,
                allergy, taste, budgetText);
        MealNutrition nutrition = analysis.getNutrition();
        double budget = analysis.getBudgetLimit();

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(rounded(Color.WHITE, 22));
        sheet.setPadding(dp(18), dp(14), dp(18), dp(16));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = txt("营养流 · 餐品分析", 18, DARK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1));
        TextView close = txt("×", 28, MUTED);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭营养分析");
        close.setOnClickListener(v -> removeOverlay());
        header.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));
        sheet.addView(header);

        TextView item = txt("当前菜品：" + shorten(rawText)
                + "\n来源：" + packageLabel(packageName)
                + " · " + nutrition.servingSummary(), 13, MUTED);
        item.setLineSpacing(0, 1.2f);
        sheet.addView(item, new LinearLayout.LayoutParams(-1, dp(62)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(txt("" + analysis.getMealLabel() + " · 每 100 克指标", 14, DARK));
        metric(body, "能量", formatNumber(nutrition.getKcalPer100()) + " kcal",
                String.format(Locale.CHINA, "本餐约 %.0f kcal · 一餐参考约 600 kcal", nutrition.getKcal()),
                nutrition.getKcal() > 600);
        metric(body, "油脂", formatNumber(nutrition.getFatPer100()) + " g",
                String.format(Locale.CHINA, "本餐约 %.1f g · 一餐参考 ≤ 8.3 g", nutrition.getFat()),
                nutrition.getFat() > 8.3);
        metric(body, "食盐", formatNumber(nutrition.getSaltPer100()) + " g",
                String.format(Locale.CHINA, "本餐约 %.1f g · 一餐参考 ≤ 1.7 g", nutrition.getSalt()),
                nutrition.getSalt() > 1.7);
        metric(body, "糖", formatNumber(nutrition.getSugarPer100()) + " g",
                String.format(Locale.CHINA, "本餐约 %.1f g · 一餐参考 ≤ 8.3 g", nutrition.getSugar()),
                nutrition.getSugar() > 8.3);
        metric(body, "蛋白质", formatNumber(nutrition.getProteinPer100()) + " g",
                String.format(Locale.CHINA, "本餐约 %.1f g · 一餐参考 ≥ 21.7 g", nutrition.getProtein()),
                nutrition.getProtein() < 21.7);
        metric(body, "膳食纤维", formatNumber(nutrition.getFiberPer100()) + " g",
                String.format(Locale.CHINA, "本餐约 %.1f g · 一餐参考 ≥ 8.3 g", nutrition.getFiber()),
                nutrition.getFiber() < 8.3);

        body.addView(txt("本次用户设置核对", 14, DARK));
        String allergyValue = analysis.hasAllergyConflict()
                ? "发现：" + join(analysis.getAllergyHits(), "、") : "未发现匹配";
        metric(body, "忌口", allergyValue,
                analysis.hasAllergyConflict() ? "页面文字命中设置中的配料，请谨慎购买" : "未命中设置中的配料关键词",
                analysis.hasAllergyConflict());
        boolean priceKnown = !Double.isNaN(price);
        metric(body, "预算", priceKnown ? String.format(Locale.CHINA, "¥%.2f", price) : "未识别价格",
                !priceKnown ? "请以商品页面价格为准，暂不判断预算" : price > budget
                        ? String.format(Locale.CHINA, "超预算 ¥%.2f", price - budget)
                        : String.format(Locale.CHINA, "低于预算 ¥%.2f", budget - price),
                price > budget);
        metric(body, "口味", analysis.getTasteResult(), "根据餐品名称和页面标签推断", false);
        String recommendations = join(analysis.getRecommendations(), "\n");
        body.addView(txt(!priceKnown ? "未识别到餐品价格，暂不提供预算搭配。" : "推荐搭配（剩余预算内）\n" + recommendations
                + String.format(Locale.CHINA, "\n搭配预算约 ¥%.2f · 餐品和搭配合计 ¥%.2f",
                analysis.getRecommendationPrice(), price + analysis.getRecommendationPrice()), 12, MUTED));

        scroll.addView(body);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Button record = action("记录到今日 · "
                + NutritionLogStore.mealName(NutritionLogStore.mealForHour(
                java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)))
                    + "（" + (int) nutrition.getPortionGrams() + "g）", GREEN);
        record.setOnClickListener(v -> {
            String meal = NutritionLogStore.mealName(NutritionLogStore.mealForHour(
                    java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)));
            NutritionLogStore.append(this, NutritionLogStore.todayKey(), meal,
                    shorten(rawText), (int) nutrition.getPortionGrams(), price, "跨应用详情");
            record.setText("已记录 ✓");
            record.setEnabled(false);
        });
        if (!priceKnown) {
            record.setEnabled(false);
            record.setText("未识别价格，请返回营养流手动记录");
        }
        sheet.addView(record, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView note = txt("点击右上角 × 可退出分析并继续浏览外卖页面。营养数据为估算，不是平台营养标签或医学诊断。", 10, MUTED);
        note.setGravity(Gravity.CENTER);
        sheet.addView(note, new LinearLayout.LayoutParams(-1, dp(40)));

        if (Build.VERSION.SDK_INT < 22) return;
        overlay = sheet;
        if (windowManager == null) windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        int type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                -1,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.75f),
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM;
        try {
            windowManager.addView(overlay, params);
        } catch (Exception ignored) {
            overlay = null;
        }
    }

    private void metric(LinearLayout parent, String label, String value,
                        String reference, boolean warning) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        TextView first = txt(label + "    " + value, 13, warning ? ORANGE : GREEN);
        first.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        line.addView(first);
        line.addView(txt(reference, 11, MUTED));
        line.setPadding(0, dp(7), 0, dp(2));
        parent.addView(line);
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.01) {
            return String.format(Locale.CHINA, "%.0f", value);
        }
        return String.format(Locale.CHINA, "%.1f", value);
    }

    private int readPortionGrams(android.content.SharedPreferences preferences) {
        String raw = preferences.getString("meal_grams", "500");
        try {
            int grams = Integer.parseInt(raw.replaceAll("[^0-9]", ""));
            return Math.max(100, Math.min(1500, grams));
        } catch (Exception ignored) {
            return 500;
        }
    }

    private String join(java.util.List<String> values, String separator) {
        if (values == null || values.isEmpty()) return "暂无合适搭配";
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(separator);
            out.append(value);
        }
        return out.toString();
    }

    private String packageLabel(String pkg) {
        if (pkg.contains("meituan")) return "美团外卖";
        if (pkg.equals("me.ele")) return "饿了么";
        if (pkg.contains("taobao")) return "淘宝闪购";
        return "京东外卖";
    }

    private String shorten(String value) {
        if (value == null || value.length() == 0) return "当前餐品";
        return value.length() > 28 ? value.substring(0, 28) + "…" : value;
    }

    private void removeOverlay() {
        overlayKey = "";
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) { }
            overlay = null;
        }
    }

    @Override
    public void onInterrupt() { hideSessionViews(); }

    @Override
    public void onDestroy() {
        cancelPendingScan();
        handler.removeCallbacksAndMessages(null);
        hideSessionViews();
        sessionPackage = "";
        super.onDestroy();
    }
}
