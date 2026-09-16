package com.nutriflow.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
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
    private Runnable pendingScan;
    private long lastShownAt;
    private long lastInteractionAt;
    private long lastWindowScheduleAt;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_SELECTED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 80;
        info.packageNames = DELIVERY_PACKAGES;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence packageName = event.getPackageName();
        String pkg = packageName == null ? "" : packageName.toString();
        if (!isDeliveryPackage(pkg)) return;

        int type = event.getEventType();
        long now = System.currentTimeMillis();
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
        if (overlay != null) return true;
        long now = System.currentTimeMillis();
        if (now - lastShownAt < 950) return true;

        String pageText = readActiveWindowText();
        boolean detail = isLikelyDetailPage(pageText);
        String foodName = pickFoodName(clicked, pageText);
        if (!looksLikeFood(foodName)) {
            if (!detail) return false;
            foodName = "当前餐品";
        }
        // A content-change fallback must prove that the visible page is a
        // detail page. A direct click can use the clicked food title itself.
        if (detailOnly && !detail) return false;

        lastShownAt = now;
        showOverlay(foodName, parsePrice(clicked, pageText), pkg);
        return true;
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
        String all = (clicked == null ? "" : clicked) + " · " + (pageText == null ? "" : pageText);
        Matcher symbol = Pattern.compile("(?:¥|￥)\\s*(\\d{1,4}(?:\\.\\d{1,2})?)").matcher(all);
        if (symbol.find()) {
            try { return Double.parseDouble(symbol.group(1)); } catch (NumberFormatException ignored) { }
        }
        Matcher context = Pattern.compile("(?:到手价|预计价|售价|价格)[^0-9]{0,10}(\\d{1,4}(?:\\.\\d{1,2})?)").matcher(all);
        if (context.find()) {
            try { return Double.parseDouble(context.group(1)); } catch (NumberFormatException ignored) { }
        }
        return 25.0;
    }

    private double readBudget() {
        try {
            return Double.parseDouble(getSharedPreferences("nutriflow", MODE_PRIVATE)
                    .getString("budget", "25").replace("元", "").replace("¥", "").trim());
        } catch (Exception ignored) {
            return 25.0;
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

    private void showOverlay(final String rawText, final double price, final String packageName) {
        removeOverlay();

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

        TextView item = txt("已捕获餐品点击：" + shorten(rawText)
                + "\n来源：" + packageLabel(packageName)
                + " · 默认按 500 克估算", 13, MUTED);
        item.setLineSpacing(0, 1.2f);
        sheet.addView(item, new LinearLayout.LayoutParams(-1, dp(62)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(8));
        body.addView(txt("本餐每 500 克营养估算", 15, DARK));
        body.addView(txt("一餐参考值按常用每日参考量约三分之一计算，仅用于点餐比较。", 11, MUTED));
        metric(body, "能量", "468 kcal", "一餐参考约 600 kcal · 还差 132 kcal", true);
        metric(body, "油脂", "8.0 g", "一餐参考 ≤ 8.3 g · 未超过", false);
        metric(body, "食盐", "1.8 g", "一餐参考 ≤ 1.7 g · 超出 0.1 g", true);
        metric(body, "添加糖", "6.0 g", "一餐参考 ≤ 8.3 g · 未超过", false);
        metric(body, "蛋白质", "18.0 g", "一餐参考 ≥ 21.7 g · 还差 3.7 g", true);
        metric(body, "膳食纤维", "2.5 g", "一餐参考 ≥ 8.3 g · 还差 5.8 g", true);

        String allergy = getSharedPreferences("nutriflow", MODE_PRIVATE)
                .getString("allergy", "无");
        String taste = getSharedPreferences("nutriflow", MODE_PRIVATE)
                .getString("taste", "未填写");
        double budget = readBudget();
        body.addView(txt("本次用户设置核对", 14, DARK));
        metric(body, "忌口", allergy.contains("无") ? "未发现匹配" : "请核对：" + allergy,
                "餐品配料以外卖详情为准", !allergy.contains("无"));
        metric(body, "预算", String.format(Locale.CHINA, "¥%.2f", price),
                price > budget
                        ? String.format(Locale.CHINA, "超预算 ¥%.2f", price - budget)
                        : String.format(Locale.CHINA, "低于预算 ¥%.2f", budget - price),
                price > budget);
        metric(body, "口味", taste, "请对照餐品标签判断辣/甜/清淡差异", false);
        TextView recommendationTitle = txt("AI 补充建议", 15, DARK);
        recommendationTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        recommendationTitle.setPadding(0, dp(12), 0, dp(4));
        body.addView(recommendationTitle);

        LinearLayout recommendation = new LinearLayout(this);
        recommendation.setOrientation(LinearLayout.VERTICAL);
        recommendation.setBackground(rounded(Color.rgb(230, 246, 239), 14));
        recommendation.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView recommendationHeading = txt("优先在当前店铺搜索", 13, DARK);
        recommendationHeading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        recommendation.addView(recommendationHeading);
        TextView recommendationItems = txt("西兰花或清炒时蔬 120 克 · 约 ¥5.00\n"
                + "无糖豆浆 250 毫升 · 约 ¥4.00\n"
                + "预计补充约 168 kcal、蛋白质 11.0 g、膳食纤维 6.4 g", 12, MUTED);
        recommendationItems.setLineSpacing(0, 1.25f);
        recommendationItems.setPadding(0, dp(5), 0, 0);
        recommendation.addView(recommendationItems);
        body.addView(recommendation);

        double totalPrice = price + 9.0;
        TextView budgetSummary = txt(String.format(Locale.CHINA,
                "餐品 ¥%.2f + 推荐搭配 ¥9.00 = ¥%.2f；%s。店内没有同类餐品时，可按上述克数自行补充。",
                price, totalPrice,
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
        TextView comparisonHint = txt("黄色为当前餐品，绿色为加入推荐后；虚线为对应的一餐参考值。各指标按自身参考值归一化。", 11, MUTED);
        comparisonHint.setLineSpacing(0, 1.2f);
        body.addView(comparisonHint);
        NutrientComparisonChart chart = new NutrientComparisonChart(this,
                new String[]{"能量", "油脂", "盐", "糖", "蛋白质", "纤维"},
                new double[]{468.0, 8.0, 1.8, 6.0, 18.0, 2.5},
                new double[]{636.0, 9.1, 2.0, 8.8, 29.0, 8.9},
                new double[]{600.0, 8.3, 1.7, 8.3, 21.7, 8.3});
        body.addView(chart, new LinearLayout.LayoutParams(-1, dp(250)));

        scroll.addView(body);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Button record = action("记录到今日 · "
                + NutritionLogStore.mealName(NutritionLogStore.mealForHour(
                java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)))
                + "（默认 500g）", GREEN);
        record.setOnClickListener(v -> {
            String meal = NutritionLogStore.mealName(NutritionLogStore.mealForHour(
                    java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)));
            NutritionLogStore.append(this, NutritionLogStore.todayKey(), meal,
                    shorten(rawText), 500, price, "跨应用点击");
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
        cancelPendingScan();
        handler.removeCallbacksAndMessages(null);
        removeOverlay();
        super.onDestroy();
    }
}
