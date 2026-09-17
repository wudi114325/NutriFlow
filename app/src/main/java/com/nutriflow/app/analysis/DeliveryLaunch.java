package com.nutriflow.app.analysis;

import android.content.Context;
import android.content.SharedPreferences;

/** One-use handoff from NutriFlow's launcher to the accessibility service. */
public final class DeliveryLaunch {
    private DeliveryLaunch() { }
    public static void prepare(Context context, String pkg) {
        preferences(context).edit().putString("pending_delivery_package", pkg)
                .putLong("pending_delivery_time", System.currentTimeMillis()).apply();
    }
    public static void cancel(Context context) {
        preferences(context).edit().remove("pending_delivery_package")
                .remove("pending_delivery_time").apply();
    }
    public static boolean consume(Context context, String pkg) {
        SharedPreferences p = preferences(context);
        if (!pkg.equals(p.getString("pending_delivery_package", ""))) return false;
        long age = System.currentTimeMillis() - p.getLong("pending_delivery_time", 0);
        cancel(context);
        if (age < 0 || age >= 120000) return false;
        p.edit().putString("active_delivery_package", pkg)
                .putLong("active_delivery_time", System.currentTimeMillis()).apply();
        return true;
    }
    // Restore only a recent explicitly launched session if Android reconnects the
    // service. Store no food/page content and never extend this lifetime by polling.
    public static String restore(Context context) {
        SharedPreferences p = preferences(context);
        long age = System.currentTimeMillis() - p.getLong("active_delivery_time", 0);
        return age >= 0 && age < 1800000 ? p.getString("active_delivery_package", "") : "";
    }
    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("nutriflow", Context.MODE_PRIVATE);
    }
}
