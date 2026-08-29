package com.maoqb.chuanlemei.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.maoqb.chuanlemei.domain.ChartSeries;

import java.util.ArrayList;
import java.util.List;

public final class BarChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barBounds = new RectF();
    private final List<ChartSeries.Point> points = new ArrayList<>();
    private final int green;
    private final int grid;
    private final int muted;

    public BarChartView(Context context) {
        this(context, 0xff167d5a, 0xffe1e7e4, 0xff6e7773);
    }

    public BarChartView(Context context, int green, int grid, int muted) {
        super(context);
        this.green = green;
        this.grid = grid;
        this.muted = muted;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setPoints(List<ChartSeries.Point> values) {
        points.clear();
        if (values != null) {
            points.addAll(values);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float left = 8 * density;
        float top = 18 * density;
        float right = getWidth() - 8 * density;
        float bottom = getHeight() - 30 * density;

        if (points.isEmpty()) {
            paint.setColor(muted);
            paint.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无趋势数据", getWidth() / 2f, getHeight() / 2f, paint);
            return;
        }

        paint.setStrokeWidth(density);
        paint.setColor(grid);
        for (int index = 0; index <= 3; index++) {
            float y = top + (bottom - top) * index / 3f;
            canvas.drawLine(left, y, right, y, paint);
        }

        int max = 1;
        for (ChartSeries.Point point : points) {
            max = Math.max(max, point.value);
        }

        float slot = (right - left) / points.size();
        float barWidth = Math.min(22 * density, slot * 0.58f);
        paint.setTextAlign(Paint.Align.CENTER);
        for (int index = 0; index < points.size(); index++) {
            ChartSeries.Point point = points.get(index);
            float center = left + slot * index + slot / 2f;
            float height = (bottom - top) * point.value / max;
            float barTop = bottom - height;

            paint.setColor(point.value == max && max > 0 ? green : (green & 0x00ffffff) | 0x99000000);
            barBounds.set(center - barWidth / 2f, barTop, center + barWidth / 2f, bottom);
            canvas.drawRoundRect(barBounds, 3 * density, 3 * density, paint);

            if (point.value > 0) {
                paint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
                if (barTop - 5 * density <= top + 10 * density) {
                    paint.setColor(0xffffffff);
                    canvas.drawText(String.valueOf(point.value), center, barTop + 16 * density, paint);
                } else {
                    paint.setColor(green);
                    canvas.drawText(String.valueOf(point.value), center, barTop - 5 * density, paint);
                }
            }

            boolean showLabel = points.size() <= 7 || index == 0 || index == points.size() - 1 || index % 2 == 0;
            if (showLabel) {
                paint.setColor(muted);
                paint.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
                canvas.drawText(point.label, center, getHeight() - 8 * density, paint);
            }
        }
    }
}
