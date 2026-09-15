package com.nutriflow.app.analysis;

import java.util.Locale;

/** Nutrition estimate for a detected meal and a concrete serving size. */
public final class MealNutrition {
    private final String category;
    private final double portionGrams;
    private final double kcalPer100;
    private final double fatPer100;
    private final double saltPer100;
    private final double sugarPer100;
    private final double proteinPer100;
    private final double fiberPer100;

    public MealNutrition(String category, double portionGrams, double kcalPer100,
                         double fatPer100, double saltPer100, double sugarPer100,
                         double proteinPer100, double fiberPer100) {
        this.category = category;
        this.portionGrams = portionGrams;
        this.kcalPer100 = kcalPer100;
        this.fatPer100 = fatPer100;
        this.saltPer100 = saltPer100;
        this.sugarPer100 = sugarPer100;
        this.proteinPer100 = proteinPer100;
        this.fiberPer100 = fiberPer100;
    }

    public String getCategory() { return category; }
    public double getPortionGrams() { return portionGrams; }
    public double getKcalPer100() { return kcalPer100; }
    public double getFatPer100() { return fatPer100; }
    public double getSaltPer100() { return saltPer100; }
    public double getSugarPer100() { return sugarPer100; }
    public double getProteinPer100() { return proteinPer100; }
    public double getFiberPer100() { return fiberPer100; }

    public double getKcal() { return scale(kcalPer100); }
    public double getFat() { return scale(fatPer100); }
    public double getSalt() { return scale(saltPer100); }
    public double getSugar() { return scale(sugarPer100); }
    public double getProtein() { return scale(proteinPer100); }
    public double getFiber() { return scale(fiberPer100); }

    private double scale(double per100) { return per100 * portionGrams / 100.0; }

    public String servingSummary() {
        return String.format(Locale.CHINA, "按 %.0f 克估算", portionGrams);
    }
}
