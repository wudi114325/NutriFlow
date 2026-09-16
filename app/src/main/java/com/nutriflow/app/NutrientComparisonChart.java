package com.nutriflow.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Locale;

/**
 * UI-only comparison chart used by the meal analysis sheet.
 * Values are normalized against each nutrient's own meal target so metrics
 * with different units can be compared in one compact chart.
 */
public final class NutrientComparisonChart extends View {
    private static final int CURRENT = Color.rgb(242, 184, 75);
    private static final int RECOMMENDED = Color.rgb(23, 150, 106);
    private static final int TARGET = Color.rgb(122, 139, 131);
    private static final int GRID = Color.rgb(221, 232, 226);
    private static final int TEXT = Color.rgb(72, 94, 85);

    private final String[] labels;
    private final double[] currentValues;
    private final double[] recommendedValues;
    private final double[] targets;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NutrientComparisonChart(Context context, String[] labels,
                                   double[] currentValues,
                                   double[] recommendedValues,
                                   double[] targets) {
        super(context);
        this.labels = labels.clone();
        this.currentValues = currentValues.clone();
        this.recommendedValues = recommendedValues.clone();
        this.targets = targets.clone();
        setMinimumHeight(dp(250));
        setContentDescription(buildDescription());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = dp(320);
        int desiredHeight = dp(250);
        int width = resolveSize(Math.max(getSuggestedMinimumWidth(), desiredWidth), widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float left = dp(12);
        float right = width - dp(12);
        float legendY = dp(15);
        float chartTop = dp(42);
        float chartBottom = height - dp(34);
        float chartHeight = chartBottom - chartTop;

        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(sp(10));
        paint.setTextAlign(Paint.Align.LEFT);
        drawLegend(canvas, left, legendY, CURRENT, "当前餐品");
        drawLegend(canvas, left + dp(82), legendY, RECOMMENDED, "加入推荐后");

        paint.setColor(TARGET);
        paint.setStrokeWidth(dp(1));
        paint.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(3)}, 0));
        canvas.drawLine(right - dp(74), legendY, right - dp(57), legendY, paint);
        paint.setPathEffect(null);
        paint.setColor(TEXT);
        canvas.drawText("一餐参考", right - dp(52), legendY + sp(3), paint);

        paint.setColor(GRID);
        paint.setStrokeWidth(dp(1));
        canvas.drawLine(left, chartBottom, right, chartBottom, paint);

        int count = Math.min(labels.length,
                Math.min(targets.length, Math.min(currentValues.length, recommendedValues.length)));
        if (count == 0) return;
        float groupWidth = (right - left) / count;
        float barWidth = Math.min(dp(14), groupWidth * 0.23f);

        for (int i = 0; i < count; i++) {
            double target = Math.max(0.01, targets[i]);
            double maximum = Math.max(target * 1.35,
                    Math.max(currentValues[i], recommendedValues[i]) * 1.08);
            float currentHeight = (float) (Math.max(0, currentValues[i]) / maximum * chartHeight);
            float recommendedHeight = (float) (Math.max(0, recommendedValues[i]) / maximum * chartHeight);
            float targetY = chartBottom - (float) (target / maximum * chartHeight);
            float centerX = left + groupWidth * (i + 0.5f);

            paint.setColor(TARGET);
            paint.setStrokeWidth(dp(1));
            paint.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(2)}, 0));
            canvas.drawLine(centerX - groupWidth * 0.34f, targetY,
                    centerX + groupWidth * 0.34f, targetY, paint);
            paint.setPathEffect(null);

            paint.setColor(CURRENT);
            canvas.drawRoundRect(new RectF(centerX - barWidth - dp(2),
                    chartBottom - currentHeight, centerX - dp(2), chartBottom),
                    dp(4), dp(4), paint);
            paint.setColor(RECOMMENDED);
            canvas.drawRoundRect(new RectF(centerX + dp(2),
                    chartBottom - recommendedHeight, centerX + barWidth + dp(2), chartBottom),
                    dp(4), dp(4), paint);

            paint.setColor(TEXT);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(10));
            canvas.drawText(labels[i], centerX, height - dp(12), paint);
        }
    }

    private void drawLegend(Canvas canvas, float x, float y, int color, String label) {
        paint.setColor(color);
        canvas.drawRoundRect(new RectF(x, y - dp(7), x + dp(12), y + dp(5)),
                dp(3), dp(3), paint);
        paint.setColor(TEXT);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(10));
        canvas.drawText(label, x + dp(17), y + sp(3), paint);
    }

    private String buildDescription() {
        StringBuilder out = new StringBuilder("营养对比图。黄色为当前餐品，绿色为加入推荐后。");
        int count = Math.min(labels.length, Math.min(currentValues.length, recommendedValues.length));
        for (int i = 0; i < count; i++) {
            out.append(String.format(Locale.CHINA, "%s当前%.1f，推荐后%.1f；",
                    labels[i], currentValues[i], recommendedValues[i]));
        }
        return out.toString();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
