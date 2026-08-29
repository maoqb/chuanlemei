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
        float size = Math.min(getWidth(), getHeight()) - 16 * density;
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        oval.set(left, top, left + size, top + size);
        float stroke = Math.max(14 * density, size * 0.16f);

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
        canvas.drawText(String.valueOf(total), getWidth() / 2f, getHeight() / 2f + 2 * density, paint);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(mutedColor);
        paint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        canvas.drawText("衣物计次", getWidth() / 2f, getHeight() / 2f + 22 * density, paint);
    }
}
