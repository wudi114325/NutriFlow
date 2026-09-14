package com.nutriflow.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Local, transparent meal log used by both the app and the optional accessibility service. */
public final class NutritionLogStore {
    private static final String PREFS = "nutriflow";
    private NutritionLogStore() { }

    public static final class Metrics {
        public double grams, energy, oil, salt, sugar, protein, fiber;
        public Metrics(double grams, double energy, double oil, double salt, double sugar, double protein, double fiber) {
            this.grams = grams; this.energy = energy; this.oil = oil; this.salt = salt; this.sugar = sugar; this.protein = protein; this.fiber = fiber;
        }
        public Metrics plus(Metrics other) { return new Metrics(grams + other.grams, energy + other.energy, oil + other.oil, salt + other.salt, sugar + other.sugar, protein + other.protein, fiber + other.fiber); }
    }

    public static String todayKey() { return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new java.util.Date()); }
    public static String displayDate(String key) { try { return new SimpleDateFormat("M月d日", Locale.CHINA).format(new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(key)); } catch (Exception e) { return key; } }
    public static String mealCode(String meal) { if (meal.contains("早餐")) return "breakfast"; if (meal.contains("午餐")) return "lunch"; if (meal.contains("晚餐")) return "dinner"; return "snack"; }
    public static String mealName(String code) { if ("breakfast".equals(code)) return "早餐"; if ("lunch".equals(code)) return "午餐"; if ("dinner".equals(code)) return "晚餐"; return "加餐"; }
    public static String mealForHour(int hour) { if (hour < 11) return "breakfast"; if (hour < 16) return "lunch"; return "dinner"; }
    private static String prefix(String date, String meal) { return "log_" + date + "_" + mealCode(meal) + "_"; }

    public static void save(Context context, String date, String meal, String name, double grams, double price, String source) {
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        String p = prefix(date, meal); e.putBoolean(p + "exists", true).putString(p + "name", name).putFloat(p + "grams", (float) grams).putFloat(p + "price", (float) price).putString(p + "source", source).apply();
    }
    public static void append(Context context, String date, String meal, String name, double grams, double price, String source) {
        if (exists(context, date, meal)) {
            String oldName = name(context, date, meal);
            double oldGrams = grams(context, date, meal);
            double oldPrice = price(context, date, meal);
            String mergedName = oldName;
            if (name != null && name.trim().length() > 0 && !oldName.contains(name.trim())) mergedName = oldName + "、" + name.trim();
            save(context, date, meal, mergedName, oldGrams + grams, oldPrice + price, source);
        } else {
            save(context, date, meal, name, grams, price, source);
        }
    }
    public static boolean exists(Context context, String date, String meal) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(prefix(date, meal) + "exists", false); }
    public static String name(Context context, String date, String meal) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(prefix(date, meal) + "name", "外卖餐品"); }
    public static double grams(Context context, String date, String meal) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(prefix(date, meal) + "grams", 500f); }
    public static String source(Context context, String date, String meal) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(prefix(date, meal) + "source", "手动记录"); }
    public static double price(Context context, String date, String meal) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(prefix(date, meal) + "price", 0f); }
    public static Metrics metrics(double grams) { double scale = grams / 100.0; return new Metrics(grams, 120 * scale, 2.4 * scale, 0.6 * scale, 2.0 * scale, 5.5 * scale, 1.8 * scale); }
    public static Metrics metrics(Context context, String date, String meal) { return metrics(grams(context, date, meal)); }
    public static Metrics daily(Context context, String date) { Metrics total = new Metrics(0, 0, 0, 0, 0, 0, 0); for (String meal : new String[]{"早餐", "午餐", "晚餐", "加餐"}) if (exists(context, date, meal)) total = total.plus(metrics(context, date, meal)); return total; }
    public static String dateFromMillis(long millis) { return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new java.util.Date(millis)); }
    public static Calendar calendar(String date) { Calendar c = Calendar.getInstance(); try { c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(date)); } catch (Exception ignored) { } return c; }
}
