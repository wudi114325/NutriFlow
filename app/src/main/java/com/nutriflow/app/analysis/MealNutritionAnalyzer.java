package com.nutriflow.app.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic, offline meal analysis used when no network AI service is available. */
public final class MealNutritionAnalyzer {
    private static final double DEFAULT_PORTION = 500.0;

    private MealNutritionAnalyzer() { }

    public static Result analyze(String foodName, String pageText, double price,
                                 double portionGrams, String allergy, String taste,
                                 String budget) {
        String name = foodName == null ? "当前餐品" : foodName.trim();
        String evidence = (name + " " + (pageText == null ? "" : pageText)).toLowerCase(Locale.CHINA);
        double grams = portionGrams > 0 ? portionGrams : DEFAULT_PORTION;
        String normalizedName = name.toLowerCase(Locale.CHINA);
        Template template = templateFor("当前餐品".equals(name) ? evidence : normalizedName);
        MealNutrition nutrition = new MealNutrition(template.category, grams,
                template.kcal, template.fat, template.salt, template.sugar,
                template.protein, template.fiber);
        List<String> allergyHits = findAllergyHits(evidence, allergy);
        String tasteResult = tasteResult(evidence, taste);
        double budgetLimit = parseBudgetLimit(budget);
        boolean priceKnown = !Double.isNaN(price) && !Double.isInfinite(price) && price >= 0;
        double comboPrice = priceKnown ? recommendedPrice(template, budgetLimit - price) : 0;
        List<String> recommendations = priceKnown ? recommendations(template, comboPrice) : new ArrayList<>();
        if (!priceKnown) recommendations.add("未识别餐品价格，暂不提供预算搭配");
        return new Result(nutrition, allergyHits, tasteResult, budgetLimit,
                comboPrice, recommendations, template.label);
    }

    private static Template templateFor(String text) {
        if (containsAny(text, "奶茶", "奶油", "蛋糕", "甜品", "冰淇淋", "饮料"))
            return new Template("甜饮/甜品", "甜品饮料", 120, 4.0, 0.35, 15.0, 2.0, 0.4);
        if (containsAny(text, "沙拉", "蔬菜", "西兰花", "轻食"))
            return new Template("蔬菜轻食", "蔬菜沙拉", 78, 3.2, 0.45, 2.6, 3.2, 3.8);
        if (containsAny(text, "汉堡", "鸡腿堡", "炸鸡", "薯条", "鸡排", "炸"))
            return new Template("油炸快餐", "汉堡/炸物", 225, 11.5, 1.15, 4.5, 10.5, 1.5);
        if (containsAny(text, "牛肉", "牛排", "烤肉", "羊肉", "烧烤", "烤串"))
            return new Template("烤肉类", "烤肉", 205, 10.0, 1.05, 1.8, 18.0, 1.2);
        if (containsAny(text, "鱼", "虾", "海鲜", "寿司"))
            return new Template("鱼虾海鲜", "海鲜主食", 165, 5.5, 0.75, 1.6, 17.0, 1.3);
        if (containsAny(text, "面", "粉", "米线", "拉面", "螺蛳粉", "馄饨", "饺子"))
            return new Template("面粉主食", "面/粉", 158, 4.8, 0.95, 2.0, 6.8, 1.8);
        if (containsAny(text, "粥", "汤"))
            return new Template("汤粥类", "汤/粥", 92, 2.2, 0.55, 1.2, 4.5, 1.4);
        if (containsAny(text, "豆浆", "牛奶", "鸡蛋", "三明治"))
            return new Template("早餐类", "早餐", 145, 5.2, 0.65, 2.2, 9.5, 1.8);
        return new Template("米饭套餐", "饭类套餐", 178, 6.3, 0.85, 2.4, 8.8, 2.1);
    }

    private static List<String> findAllergyHits(String text, String allergy) {
        List<String> hits = new ArrayList<>();
        if (allergy == null || allergy.trim().length() == 0 || allergy.contains("无")) return hits;
        String[] tokens = allergy.split("[、,，;；/\\s]+", -1);
        for (String token : tokens) {
            String clean = token.trim().toLowerCase(Locale.CHINA)
                    .replace("过敏", "").replace("忌口", "")
                    .replace("不吃", "").replace("不要", "");
            if (clean.length() < 1) continue;
            String[] aliases = aliases(clean);
            for (String alias : aliases) {
                if (text.contains(alias)) {
                    hits.add(token.trim());
                    break;
                }
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(hits));
    }

    private static String[] aliases(String token) {
        if (token.contains("奶") || token.contains("乳")) return new String[]{token, "牛奶", "乳制品", "奶油", "芝士", "奶酪"};
        if (token.contains("蛋")) return new String[]{token, "鸡蛋", "蛋液", "蛋黄"};
        if (token.contains("花生")) return new String[]{token, "花生", "坚果"};
        if (token.contains("麸")) return new String[]{token, "面粉", "小麦", "面包"};
        if (token.contains("海鲜") || token.contains("鱼")) return new String[]{token, "鱼", "虾", "蟹", "海鲜"};
        return new String[]{token};
    }

    private static String tasteResult(String text, String taste) {
        String preference = taste == null ? "" : taste;
        boolean spicy = containsAny(text, "辣", "麻辣", "香辣", "剁椒");
        boolean sweet = containsAny(text, "甜", "奶茶", "糖", "蛋糕");
        boolean greasy = containsAny(text, "炸", "油", "肥", "薯条");
        if (preference.contains("辣") && spicy) return "匹配：餐品含辣味";
        if (preference.contains("清淡") || preference.contains("少油")) {
            if (greasy || spicy) return "需注意：餐品偏油或偏辣";
            return "匹配：整体口味较清淡";
        }
        if (preference.contains("甜") && sweet) return "匹配：餐品含甜味";
        if (preference.contains("高蛋白")) return "建议搭配高蛋白食物";
        if (spicy) return "餐品偏辣，请结合个人口味选择";
        return "未发现明显口味冲突";
    }

    private static double parseBudgetLimit(String value) {
        if (value == null) return 25.0;
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(value.replace(",", ""));
        double first = -1;
        double second = -1;
        if (matcher.find()) first = Double.parseDouble(matcher.group(1));
        if (matcher.find()) second = Double.parseDouble(matcher.group(1));
        if (second >= 0) return second;
        if (first >= 0) return first;
        if (value.contains("弹性")) return 50.0;
        return 25.0;
    }

    private static double recommendedPrice(Template template, double remaining) {
        if (remaining < 3.0) return 0;
        if (template.category.equals("甜饮/甜品")) {
            if (remaining >= 7.0) return 7.0;
            if (remaining >= 4.0) return 4.0;
            return 3.0;
        }
        if (template.category.equals("蔬菜轻食")) return remaining >= 4.0 ? 4.0 : 3.0;
        if (remaining >= 9.0) return 9.0;
        if (remaining >= 5.0) return 5.0;
        if (remaining >= 4.0) return 4.0;
        return 3.0;
    }

    private static List<String> recommendations(Template template, double price) {
        List<String> items = new ArrayList<>();
        if (price < 3.0) {
            items.add("当前餐品已达到预算上限，暂不增加搭配");
            return items;
        }
        if (template.category.equals("甜饮/甜品")) {
            if (price >= 4.0) items.add("无糖豆浆 250 毫升 · 约 ¥4.00");
            if (price >= 7.0) items.add("水煮蛋 1 个 · 约 ¥3.00");
            if (price == 3.0) items.add("水煮蛋 1 个 · 约 ¥3.00");
        } else if (template.category.equals("蔬菜轻食")) {
            if (price >= 4.0) items.add("无糖豆浆 250 毫升 · 约 ¥4.00");
            else items.add("水煮蛋 1 个 · 约 ¥3.00");
        } else {
            if (price >= 5.0) items.add("西兰花或清炒时蔬 120 克 · 约 ¥5.00");
            if (price >= 9.0) items.add("无糖豆浆 250 毫升 · 约 ¥4.00");
            if (price == 4.0) items.add("无糖豆浆 250 毫升 · 约 ¥4.00");
            if (price == 3.0) items.add("水煮蛋 1 个 · 约 ¥3.00");
        }
        return items;
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static final class Template {
        final String category;
        final String label;
        final double kcal, fat, salt, sugar, protein, fiber;
        Template(String category, String label, double kcal, double fat, double salt,
                 double sugar, double protein, double fiber) {
            this.category = category; this.label = label; this.kcal = kcal; this.fat = fat;
            this.salt = salt; this.sugar = sugar; this.protein = protein; this.fiber = fiber;
        }
    }

    public static final class Result {
        private final MealNutrition nutrition;
        private final List<String> allergyHits;
        private final String tasteResult;
        private final double budgetLimit;
        private final double recommendationPrice;
        private final List<String> recommendations;
        private final String mealLabel;

        Result(MealNutrition nutrition, List<String> allergyHits, String tasteResult,
               double budgetLimit, double recommendationPrice, List<String> recommendations,
               String mealLabel) {
            this.nutrition = nutrition; this.allergyHits = allergyHits; this.tasteResult = tasteResult;
            this.budgetLimit = budgetLimit; this.recommendationPrice = recommendationPrice;
            this.recommendations = recommendations; this.mealLabel = mealLabel;
        }
        public MealNutrition getNutrition() { return nutrition; }
        public List<String> getAllergyHits() { return allergyHits; }
        public String getTasteResult() { return tasteResult; }
        public double getBudgetLimit() { return budgetLimit; }
        public double getRecommendationPrice() { return recommendationPrice; }
        public List<String> getRecommendations() { return recommendations; }
        public String getMealLabel() { return mealLabel; }
        public boolean hasAllergyConflict() { return !allergyHits.isEmpty(); }
    }
}
