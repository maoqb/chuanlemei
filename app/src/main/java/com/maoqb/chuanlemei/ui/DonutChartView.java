package com.maoqb.chuanlemei.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

public final class DonutChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int[] values = new int[0];
    private int[] colors = new int[0];
    private int textColor;
    private int mutedColor;
    private int trackColor;

    public DonutChartView(Context context) {
        super(context);
    }

    public void setData(int[] values, int[] colors, int textColor, int mutedColor, int trackColor) {
        this.values = values == null ? new int[0] : values.clone();
        this.colors = colors == null ? new int[0] : colors.clone();
        this.textColor = textColor;
        this.mutedColor = mutedColor;
        this.trackColor = trackColor;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float available = Math.min(getWidth(), getHeight());
        float stroke = Math.max(14 * density, available * 0.16f);
        float edgePadding = 8 * density;
        float diameter = Math.max(0, available - stroke - edgePadding * 2);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = diameter / 2f;
        oval.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setColor(trackColor);
        canvas.drawArc(oval, -90, 360, false, paint);

        int total = 0;
        for (int value : values) {
            total += Math.max(0, value);
        }
        if (total > 0) {
            float start = -90;
            for (int index = 0; index < values.length && index < colors.length; index++) {
                if (values[index] <= 0) {
                    continue;
                }
                float sweep = 360f * values[index] / total;
                paint.setColor(colors[index]);
                canvas.drawArc(oval, start, sweep, false, paint);
                start += sweep;
            }
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(textColor);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(26 * getResources().getDisplayMetrics().scaledDensity);
        canvas.drawText(String.valueOf(total), centerX, centerY + 2 * density, paint);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(mutedColor);
        paint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        canvas.drawText("衣物计次", centerX, centerY + 22 * density, paint);
    }
}
