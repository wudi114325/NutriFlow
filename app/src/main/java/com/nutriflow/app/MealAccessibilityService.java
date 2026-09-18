package com.nutriflow.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.nutriflow.app.analysis.MealNutrition;
import com.nutriflow.app.analysis.MealNutritionAnalyzer;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Cross-app bridge for the supported delivery apps.
 *
 * A delivery app often reports the click on an image/container while the
 * food title is rendered in a sibling node. The service therefore waits for
 * the detail page and scans the visible accessibility tree before showing the
 * analysis sheet.
 */
public class MealAccessibilityService extends AccessibilityService {
    private static final String LOG_TAG = "NutriFlowAccess";
    static final String PREF_ANALYSIS_ENABLED = "cross_app_analysis_enabled";
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
    private static final String[] DETAIL_SIGNALS = {
            "加入购物车", "立即购买", "选规格", "商品描述", "套餐详情",
            "口味", "规格", "配送费", "到手价"
    };
    private static final String[] FOOD_SIGNALS = {
            "饭", "面", "粉", "粥", "肉", "鸡", "鸭", "牛", "羊", "鱼",
            "虾", "菜", "蛋", "堡", "汉堡", "沙拉", "披萨", "寿司",
            "烧烤", "烤", "炸", "汤", "米线", "盖饭", "奶茶", "咖啡",
            "豆浆", "三明治", "套餐", "小吃", "卷", "饺子", "馄饨"
    };
    private static final String[] GENERIC_WORDS = {
            "返回", "首页", "购物车", "搜索", "确认", "取消", "更多", "评论",
            "收藏", "立即购买", "加入购物车", "优惠券", "店铺", "商品详情",
            "商品", "详情", "规格", "数量", "配送", "地址", "美团", "饿了么",
            "淘宝", "京东", "全部", "分类", "推荐", "已售", "月售", "营业中"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlay;
    private TextView floatingSwitch;
    private Runnable pendingScan;
    private String currentDeliveryPackage = "";
    private String dismissedFoodKey = "";
    private boolean screenshotRecognitionRunning;
    private long lastScreenshotRecognitionAt;
    private boolean serviceDestroyed;
    private long lastShownAt;
    private long lastInteractionAt;
    private long lastWindowScheduleAt;
    private final Runnable floatingSwitchMonitor = new Runnable() {
        @Override public void run() {
            if (floatingSwitch == null || serviceDestroyed) return;
            String foreground = foregroundPackage();
            if (!isDeliveryPackage(foreground)) {
                currentDeliveryPackage = "";
                cancelPendingScan();
                removeOverlay();
                removeFloatingSwitch();
                return;
            }
            currentDeliveryPackage = foreground;
            handler.postDelayed(this, 1200);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        serviceDestroyed = false;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_SELECTED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 80;
        // Listen only to supported delivery apps. A lightweight foreground
        // check removes our switch when the user leaves one of them.
        info.packageNames = DELIVERY_PACKAGES;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
        Log.d(LOG_TAG, "Service connected with delivery-package window events");
        handler.postDelayed(() -> {
            String activePackage = foregroundPackage();
            if (!isDeliveryPackage(activePackage)) return;
            currentDeliveryPackage = activePackage;
            showFloatingSwitch();
            if (isAnalysisEnabled()) tryShowAnalysis(activePackage, "", true);
        }, 700);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence packageName = event.getPackageName();
        String pkg = packageName == null ? "" : packageName.toString();

        int type = event.getEventType();
        long now = System.currentTimeMillis();
        if ("com.taobao.taobao".equals(pkg)
                && type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(LOG_TAG, "Taobao window event received");
        }
        if (!isDeliveryPackage(pkg)) {
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                String foreground = foregroundPackage();
                if (!isDeliveryPackage(foreground)) {
                    currentDeliveryPackage = "";
                    cancelPendingScan();
                    removeFloatingSwitch();
                    removeOverlay();
                }
            }
            return;
        }

        currentDeliveryPackage = pkg;
        showFloatingSwitch();
        if (!isAnalysisEnabled()) return;
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            lastInteractionAt = now;
            String clicked = readClickedText(event);
            scheduleClickScan(pkg, clicked);
            return;
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // A few clients do not emit TYPE_VIEW_CLICKED. For those clients,
            // a detail-page tree change is the reliable fallback.
            if (now - lastInteractionAt < 900) return;
            if (now - lastWindowScheduleAt < 900) return;
            lastWindowScheduleAt = now;
            scheduleDetailScan(pkg);
        }
    }

    private boolean isDeliveryPackage(String pkg) {
        for (String known : DELIVERY_PACKAGES) if (known.equals(pkg)) return true;
        return false;
    }

    private void scheduleClickScan(final String pkg, final String clicked) {
        cancelPendingScan();
        pendingScan = new Runnable() {
            @Override public void run() {
                if (tryShowAnalysis(pkg, clicked, false)) return;
                // The detail page may need another frame to finish rendering.
                pendingScan = new Runnable() {
                    @Override public void run() { tryShowAnalysis(pkg, clicked, false); }
                };
                handler.postDelayed(pendingScan, 480);
            }
        };
        handler.postDelayed(pendingScan, 320);
    }

    private void scheduleDetailScan(final String pkg) {
        cancelPendingScan();
        pendingScan = new Runnable() {
            @Override public void run() { tryShowAnalysis(pkg, "", true); }
        };
        handler.postDelayed(pendingScan, 420);
    }

    private void cancelPendingScan() {
        if (pendingScan != null) {
            handler.removeCallbacks(pendingScan);
            pendingScan = null;
        }
    }

    private boolean tryShowAnalysis(String pkg, String clicked, boolean detailOnly) {
        if (!isAnalysisEnabled() || !pkg.equals(currentDeliveryPackage)) return false;
        if (overlay != null) return true;
        long now = System.currentTimeMillis();
        if (now - lastShownAt < 950) return true;

        String pageText = readActiveWindowText();
        boolean detail = isLikelyDetailPage(pageText);
        String foodName = pickFoodName(clicked, pageText);
        if ("com.taobao.taobao".equals(pkg) && !detail) {
            recognizeTaobaoScreenshot(pkg, clicked);
            return false;
        }
        if (!detail && clicked.length() == 0) dismissedFoodKey = "";
        if (!looksLikeFood(foodName)) {
            if (!detail) return false;
            foodName = "当前餐品";
        }
        // A content-change fallback must prove that the visible page is a
        // detail page. A direct click can use the clicked food title itself.
        if (detailOnly && !detail) return false;
        if ((pkg + "|" + foodName).equals(dismissedFoodKey)) return false;

        lastShownAt = now;
        showOverlay(foodName, pageText, parsePrice(clicked, pageText), pkg);
        return true;
    }

    private void recognizeTaobaoScreenshot(final String pkg, final String clicked) {
        if (Build.VERSION.SDK_INT < 30 || screenshotRecognitionRunning
                || !isAnalysisEnabled() || !pkg.equals(currentDeliveryPackage)) return;
        long now = System.currentTimeMillis();
        if (now - lastScreenshotRecognitionAt < 2500) return;
        lastScreenshotRecognitionAt = now;
        screenshotRecognitionRunning = true;
        Log.d(LOG_TAG, "Taobao screenshot recognition requested");
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult screenshot) {
                    Log.d(LOG_TAG, "Taobao screenshot received");
                    Bitmap image = null;
                    HardwareBuffer buffer = screenshot.getHardwareBuffer();
                    try {
                        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                        if (hardware != null) {
                            image = hardware.copy(Bitmap.Config.ARGB_8888, false);
                            hardware.recycle();
                        }
                    } catch (Exception ignored) { }
                    finally { buffer.close(); }
                    if (image == null) {
                        screenshotRecognitionRunning = false;
                        return;
                    }
                    final Bitmap bitmap = image;
                    final TextRecognizer recognizer = TextRecognition.getClient(
                            new ChineseTextRecognizerOptions.Builder().build());
                    try {
                        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                                .addOnSuccessListener(result -> {
                                    String foreground = foregroundPackage();
                                    Log.d(LOG_TAG, "Taobao OCR gate foreground=" + foreground
                                            + " current=" + currentDeliveryPackage
                                            + " enabled=" + isAnalysisEnabled()
                                            + " overlay=" + (overlay != null));
                                    if (serviceDestroyed || !isAnalysisEnabled()
                                            || !pkg.equals(foreground)
                                            || overlay != null || result == null) return;
                                    String text = result.getText();
                                    boolean detailPage = isLikelyDetailPage(text);
                                    Log.d(LOG_TAG, "Taobao OCR chars=" + text.length()
                                            + " detail=" + detailPage);
                                    if (!detailPage) {
                                        dismissedFoodKey = "";
                                        return;
                                    }
                                    String titleArea = text;
                                    int descriptionAt = text.indexOf("商品描述");
                                    int recommendationsAt = text.indexOf("搭配推荐");
                                    int end = descriptionAt >= 0 ? descriptionAt : text.length();
                                    if (recommendationsAt >= 0) end = Math.min(end, recommendationsAt);
                                    if (end > 0) titleArea = text.substring(0, end);
                                    String foodName = pickFoodName(clicked, titleArea);
                                    Log.d(LOG_TAG, "Taobao food candidate valid="
                                            + looksLikeFood(foodName) + " length=" + foodName.length());
                                    if (!looksLikeFood(foodName)
                                            || (pkg + "|" + foodName).equals(dismissedFoodKey)) return;
                                    lastShownAt = System.currentTimeMillis();
                                    showOverlay(foodName, text, parsePrice(clicked, text), pkg);
                                })
                                .addOnCompleteListener(task -> {
                                    bitmap.recycle();
                                    recognizer.close();
                                    screenshotRecognitionRunning = false;
                                });
                    } catch (Exception ignored) {
                        bitmap.recycle();
                        recognizer.close();
                        screenshotRecognitionRunning = false;
                    }
                }

                @Override public void onFailure(int errorCode) {
                    Log.d(LOG_TAG, "Taobao screenshot error=" + errorCode);
                    screenshotRecognitionRunning = false;
                }
            });
        } catch (Exception error) {
            Log.d(LOG_TAG, "Taobao screenshot request failed", error);
            screenshotRecognitionRunning = false;
        }
    }

    private boolean isAnalysisEnabled() {
        return getSharedPreferences("nutriflow", MODE_PRIVATE)
                .getBoolean(PREF_ANALYSIS_ENABLED, true);
    }

    private void setAnalysisEnabled(boolean enabled) {
        getSharedPreferences("nutriflow", MODE_PRIVATE).edit()
                .putBoolean(PREF_ANALYSIS_ENABLED, enabled).apply();
        cancelPendingScan();
        if (!enabled) removeOverlay();
        else {
            dismissedFoodKey = "";
            lastShownAt = 0;
            lastScreenshotRecognitionAt = 0;
            handler.postDelayed(() -> {
                String activePackage = foregroundPackage();
                if (!isAnalysisEnabled() || !isDeliveryPackage(activePackage)) return;
                currentDeliveryPackage = activePackage;
                tryShowAnalysis(activePackage, "", true);
            }, 350);
        }
        updateFloatingSwitch();
    }

    private String foregroundPackage() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo appRoot = window.getRoot();
                if (appRoot == null) continue;
                CharSequence appName = appRoot.getPackageName();
                String appPackage = appName == null ? "" : appName.toString();
                appRoot.recycle();
                if (appPackage.length() > 0) return appPackage;
            }
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        CharSequence name = root.getPackageName();
        String result = name == null ? "" : name.toString();
        root.recycle();
        return result;
    }

    private void showFloatingSwitch() {
        if (floatingSwitch != null || Build.VERSION.SDK_INT < 22) return;
        TextView control = txt("", 12, Color.WHITE);
        control.setGravity(Gravity.CENTER);
        control.setPadding(dp(5), 0, dp(5), 0);
        control.setOnClickListener(v -> setAnalysisEnabled(!isAnalysisEnabled()));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(76), dp(44), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.START | Gravity.TOP;
        params.y = dp(98);
        try {
            windowManager.addView(control, params);
            floatingSwitch = control;
            updateFloatingSwitch();
            handler.removeCallbacks(floatingSwitchMonitor);
            handler.postDelayed(floatingSwitchMonitor, 1200);
        } catch (Exception error) { Log.d(LOG_TAG, "Floating switch unavailable", error); }
    }

    private void updateFloatingSwitch() {
        if (floatingSwitch == null) return;
        boolean enabled = isAnalysisEnabled();
        floatingSwitch.setText(enabled ? "分析 开" : "分析 关");
        floatingSwitch.setContentDescription(enabled
                ? "营养流分析已开启，点击暂停" : "营养流分析已暂停，点击恢复");
        floatingSwitch.setBackground(rounded(enabled ? GREEN : MUTED, 14));
    }

    private void removeFloatingSwitch() {
        handler.removeCallbacks(floatingSwitchMonitor);
        if (floatingSwitch == null || windowManager == null) return;
        try { windowManager.removeView(floatingSwitch); } catch (Exception ignored) { }
        floatingSwitch = null;
    }

    private String readClickedText(AccessibilityEvent event) {
        StringBuilder out = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (CharSequence item : event.getText()) appendUnique(out, seen, item);
        appendUnique(out, seen, event.getContentDescription());

        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            appendNodeText(source, out, seen, 0);
            AccessibilityNodeInfo parent = source.getParent();
            if (parent != null) {
                appendNodeText(parent, out, seen, 0);
                parent.recycle();
            }
            source.recycle();
        }
        return out.toString().trim();
    }

    private String readActiveWindowText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        StringBuilder out = new StringBuilder();
        appendNodeText(root, out, new HashSet<String>(), 0);
        root.recycle();
        return out.toString().trim();
    }

    private void appendNodeText(AccessibilityNodeInfo node, StringBuilder out,
                                Set<String> seen, int depth) {
        if (node == null || depth > 24 || out.length() > 12000) return;
        appendUnique(out, seen, node.getText());
        appendUnique(out, seen, node.getContentDescription());

        int count = Math.min(node.getChildCount(), 80);
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                appendNodeText(child, out, seen, depth + 1);
                child.recycle();
            }
        }
    }

    private void appendUnique(StringBuilder out, Set<String> seen, CharSequence value) {
        if (value == null) return;
        String text = value.toString().replace('\n', ' ').replace('\r', ' ').trim();
        if (text.length() == 0 || text.length() > 180 || seen.contains(text)) return;
        seen.add(text);
        if (out.length() > 0) out.append(" · ");
        out.append(text);
    }

    private boolean isLikelyDetailPage(String text) {
        if (text == null || text.length() == 0) return false;
        int hits = 0;
        for (String signal : DETAIL_SIGNALS) if (text.contains(signal)) hits++;
        boolean hasPrice = Pattern.compile("(?:¥|￥)\\s*\\d").matcher(text).find();
        return hits >= 1 && hasPrice;
    }

    private String pickFoodName(String clicked, String pageText) {
        String best = "";
        int bestScore = -1000;
        String[] clickedParts = splitCandidates(clicked);
        for (String part : clickedParts) {
            int score = foodScore(part);
            if (score > bestScore) { bestScore = score; best = part; }
        }
        String[] pageParts = splitCandidates(pageText);
        for (String part : pageParts) {
            int score = foodScore(part);
            if (score > bestScore) { bestScore = score; best = part; }
        }
        return bestScore >= 1 ? best : "";
    }

    private String[] splitCandidates(String value) {
        if (value == null) return new String[0];
        return value.split("[\\n\\r·|•]+");
    }

    private int foodScore(String value) {
        if (!looksLikeFood(value)) return -1000;
        String text = value.trim();
        int score = 1;
        for (String signal : FOOD_SIGNALS) if (text.contains(signal)) score += 4;
        for (String generic : GENERIC_WORDS) if (text.equals(generic)) score -= 10;
        if (text.length() >= 3 && text.length() <= 22) score += 2;
        if (text.length() > 38) score -= 5;
        if (text.matches(".*\\d{2}:\\d{2}.*")) score -= 8;
        if (text.matches(".*\\d{4,}.*")) score -= 5;
        if (text.contains("营养流") || text.contains("分析")) score -= 12;
        return score;
    }

    private boolean looksLikeFood(String text) {
        if (text == null) return false;
        String value = text.replace(" ", "").trim();
        if (value.length() < 2 || value.length() > 60) return false;
        if (!value.matches(".*[\\u4e00-\\u9fffA-Za-z].*")) return false;
        if (value.matches("^[0-9.¥￥元元起+\\-]+$")) return false;
        for (String generic : GENERIC_WORDS) if (value.equals(generic)) return false;
        return true;
    }

    private double parsePrice(String clicked, String pageText) {
        return DisplayedPriceParser.parse(clicked, pageText);
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
        close.setOnClickListener(v -> {
            dismissedFoodKey = packageName + "|" + rawText;
            removeOverlay();
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));
        sheet.addView(header);

        TextView item = txt("已捕获餐品点击：" + shorten(rawText)
                + "\n来源：" + packageLabel(packageName)
                + " · " + nutrition.servingSummary(), 13, MUTED);
        item.setLineSpacing(0, 1.2f);
        sheet.addView(item, new LinearLayout.LayoutParams(-1, dp(62)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(8));
        body.addView(txt("本餐每 " + (int) nutrition.getPortionGrams()
                + " 克营养估算 · " + analysis.getMealLabel(), 15, DARK));
        body.addView(txt("根据餐品名称离线估算；一餐参考值仅用于点餐比较。", 11, MUTED));
        metric(body, "能量", formatNumber(nutrition.getKcal()) + " kcal",
                "一餐参考约 600 kcal · " + energyDifferenceText(nutrition.getKcal()),
                Math.abs(nutrition.getKcal() - 600) > 100);
        metric(body, "油脂", formatNumber(nutrition.getFat()) + " g",
                "一餐参考 ≤ 8.3 g · " + differenceText(nutrition.getFat(), 8.3, true),
                nutrition.getFat() > 8.3);
        metric(body, "食盐", formatNumber(nutrition.getSalt()) + " g",
                "一餐参考 ≤ 1.7 g · " + differenceText(nutrition.getSalt(), 1.7, true),
                nutrition.getSalt() > 1.7);
        metric(body, "糖", formatNumber(nutrition.getSugar()) + " g",
                "一餐参考 ≤ 8.3 g · " + differenceText(nutrition.getSugar(), 8.3, true),
                nutrition.getSugar() > 8.3);
        metric(body, "蛋白质", formatNumber(nutrition.getProtein()) + " g",
                "一餐参考 ≥ 21.7 g · " + differenceText(nutrition.getProtein(), 21.7, false),
                nutrition.getProtein() < 21.7);
        metric(body, "膳食纤维", formatNumber(nutrition.getFiber()) + " g",
                "一餐参考 ≥ 8.3 g · " + differenceText(nutrition.getFiber(), 8.3, false),
                nutrition.getFiber() < 8.3);

        body.addView(txt("本次用户设置核对", 14, DARK));
        String allergyValue = analysis.hasAllergyConflict()
                ? "发现：" + join(analysis.getAllergyHits(), "、") : "未发现匹配";
        metric(body, "忌口", allergyValue,
                analysis.hasAllergyConflict() ? "页面文字命中设置中的配料，请谨慎购买" : "未命中设置中的配料关键词",
                analysis.hasAllergyConflict());
        metric(body, "预算", String.format(Locale.CHINA, "¥%.2f", price),
                price > budget
                        ? String.format(Locale.CHINA, "超预算 ¥%.2f", price - budget)
                        : String.format(Locale.CHINA, "低于预算 ¥%.2f", budget - price),
                price > budget);
        metric(body, "口味", analysis.getTasteResult(), "根据餐品名称和页面标签推断", false);
        TextView recommendationTitle = txt("补充建议（离线估算）", 15, DARK);
        recommendationTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        recommendationTitle.setPadding(0, dp(12), 0, dp(4));
        body.addView(recommendationTitle);

        LinearLayout recommendation = new LinearLayout(this);
        recommendation.setOrientation(LinearLayout.VERTICAL);
        recommendation.setBackground(rounded(Color.rgb(230, 246, 239), 14));
        recommendation.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView recommendationHeading = txt("可先在当前店铺搜索", 13, DARK);
        recommendationHeading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        recommendation.addView(recommendationHeading);
        TextView recommendationItems = txt(join(analysis.getRecommendations(), "\n"), 12, MUTED);
        recommendationItems.setLineSpacing(0, 1.25f);
        recommendationItems.setPadding(0, dp(5), 0, 0);
        recommendation.addView(recommendationItems);
        body.addView(recommendation);

        double recommendationPrice = analysis.getRecommendationPrice();
        double totalPrice = price + recommendationPrice;
        TextView budgetSummary = txt(String.format(Locale.CHINA,
                "餐品 ¥%.2f + 推荐搭配 ¥%.2f = ¥%.2f；%s。店内没有同类餐品时，可按上述份量自行补充。",
                price, recommendationPrice, totalPrice,
                totalPrice > budget
                        ? String.format(Locale.CHINA, "合计超预算 ¥%.2f", totalPrice - budget)
                        : String.format(Locale.CHINA, "合计仍低于预算 ¥%.2f", budget - totalPrice)),
                11, MUTED);
        budgetSummary.setLineSpacing(0, 1.2f);
        budgetSummary.setPadding(0, dp(7), 0, 0);
        body.addView(budgetSummary);

        TextView comparisonTitle = txt("补充前后营养对比", 15, DARK);
        comparisonTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        comparisonTitle.setPadding(0, dp(14), 0, 0);
        body.addView(comparisonTitle);
        TextView comparisonHint = txt("黄色为当前餐品，绿色为加入推荐后；虚线为一餐参考值。搭配后的增量也是粗略估算，各指标按自身参考值归一化。", 11, MUTED);
        comparisonHint.setLineSpacing(0, 1.2f);
        body.addView(comparisonHint);
        double[] current = {nutrition.getKcal(), nutrition.getFat(), nutrition.getSalt(),
                nutrition.getSugar(), nutrition.getProtein(), nutrition.getFiber()};
        double[] added = estimateRecommendationNutrition(analysis.getRecommendations());
        double[] combined = new double[current.length];
        for (int i = 0; i < current.length; i++) combined[i] = current[i] + added[i];
        NutrientComparisonChart chart = new NutrientComparisonChart(this,
                new String[]{"能量", "油脂", "盐", "糖", "蛋白质", "纤维"},
                current, combined,
                new double[]{600.0, 8.3, 1.7, 8.3, 21.7, 8.3});
        body.addView(chart, new LinearLayout.LayoutParams(-1, dp(250)));

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
                    shorten(rawText), (int) nutrition.getPortionGrams(), price, "跨应用点击");
            record.setText("已记录 ✓");
            record.setEnabled(false);
        });
        sheet.addView(record, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView note = txt("点击右上角 × 可退出分析并继续浏览外卖页面。营养数据为估算，不是平台营养标签或医学诊断。", 10, MUTED);
        note.setGravity(Gravity.CENTER);
        sheet.addView(note, new LinearLayout.LayoutParams(-1, dp(40)));

        overlay = sheet;
        if (windowManager == null) windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= 22
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                -1,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.75f),
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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

    private String energyDifferenceText(double value) {
        if (value > 600) return "超出 " + formatNumber(value - 600) + " kcal";
        if (value < 600) return "还差 " + formatNumber(600 - value) + " kcal";
        return "接近参考";
    }

    private String differenceText(double value, double target, boolean upperLimit) {
        if (upperLimit) {
            return value > target ? "超出 " + formatNumber(value - target) + " g" : "未超过";
        }
        return value < target ? "还差 " + formatNumber(target - value) + " g" : "已达到";
    }

    private double[] estimateRecommendationNutrition(java.util.List<String> recommendations) {
        // Generic item estimates are used only to keep the comparison chart in sync
        // with the offline recommendation list; they are not merchant nutrition labels.
        double[] added = new double[6];
        for (String item : recommendations) {
            if (item.contains("西兰花") || item.contains("清炒时蔬")) {
                addNutrition(added, 50, 1.2, 0.25, 2.0, 3.0, 3.5);
            } else if (item.contains("豆浆")) {
                addNutrition(added, 90, 3.0, 0.2, 1.5, 7.5, 1.0);
            } else if (item.contains("水煮蛋")) {
                addNutrition(added, 75, 5.0, 0.2, 0.2, 6.0, 0);
            }
        }
        return added;
    }

    private void addNutrition(double[] values, double kcal, double fat, double salt,
                              double sugar, double protein, double fiber) {
        values[0] += kcal;
        values[1] += fat;
        values[2] += salt;
        values[3] += sugar;
        values[4] += protein;
        values[5] += fiber;
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
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) { }
            overlay = null;
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        serviceDestroyed = true;
        cancelPendingScan();
        handler.removeCallbacksAndMessages(null);
        removeOverlay();
        removeFloatingSwitch();
        super.onDestroy();
    }
}
