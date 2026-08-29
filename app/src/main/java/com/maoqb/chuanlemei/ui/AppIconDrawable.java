package com.maoqb.chuanlemei.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public final class AppIconDrawable extends Drawable {
    public static final String HOME = "home";
    public static final String WARDROBE = "wardrobe";
    public static final String OUTFIT = "outfit";
    public static final String STATS = "stats";
    public static final String CAMERA = "camera";
    public static final String PLUS = "plus";
    public static final String CALENDAR = "calendar";
    public static final String TRASH = "trash";
    public static final String CHECK = "check";
    public static final String CHEVRON = "chevron";
    public static final String CLOSE = "close";

    private final String icon;
    private final int size;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public AppIconDrawable(String icon, int color, int size, float strokeWidth) {
        this.icon = icon;
        this.size = size;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        float scaleX = getBounds().width() / 24f;
        float scaleY = getBounds().height() / 24f;
        canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(scaleX, scaleY);
        path.reset();

        switch (icon) {
            case HOME:
                path.moveTo(3.5f, 10.5f);
                path.lineTo(12, 3.5f);
                path.lineTo(20.5f, 10.5f);
                path.moveTo(5.5f, 9.5f);
                path.lineTo(5.5f, 20);
                path.lineTo(18.5f, 20);
                path.lineTo(18.5f, 9.5f);
                path.moveTo(10, 20);
                path.lineTo(10, 14);
                path.lineTo(14, 14);
                path.lineTo(14, 20);
                canvas.drawPath(path, paint);
                break;
            case WARDROBE:
                canvas.drawRoundRect(new RectF(4, 3, 20, 21), 2, 2, paint);
                canvas.drawLine(12, 3, 12, 21, paint);
                canvas.drawCircle(9.5f, 12, 0.7f, paint);
                canvas.drawCircle(14.5f, 12, 0.7f, paint);
                break;
            case OUTFIT:
                path.moveTo(8, 4);
                path.lineTo(5, 6);
                path.lineTo(2.8f, 10);
                path.lineTo(6.5f, 12);
                path.lineTo(7.2f, 20);
                path.lineTo(16.8f, 20);
                path.lineTo(17.5f, 12);
                path.lineTo(21.2f, 10);
                path.lineTo(19, 6);
                path.lineTo(16, 4);
                path.cubicTo(15.2f, 6.2f, 13.8f, 7.2f, 12, 7.2f);
                path.cubicTo(10.2f, 7.2f, 8.8f, 6.2f, 8, 4);
                canvas.drawPath(path, paint);
                break;
            case STATS:
                canvas.drawRoundRect(new RectF(4, 13, 7.5f, 20), 1, 1, paint);
                canvas.drawRoundRect(new RectF(10.2f, 8.5f, 13.8f, 20), 1, 1, paint);
                canvas.drawRoundRect(new RectF(16.5f, 4, 20, 20), 1, 1, paint);
                break;
            case CAMERA:
                path.moveTo(4, 7.5f);
                path.lineTo(7.2f, 7.5f);
                path.lineTo(8.6f, 5);
                path.lineTo(15.4f, 5);
                path.lineTo(16.8f, 7.5f);
                path.lineTo(20, 7.5f);
                path.cubicTo(21, 7.5f, 21, 8.3f, 21, 9.2f);
                path.lineTo(21, 18);
                path.cubicTo(21, 19, 20.2f, 19.8f, 19.2f, 19.8f);
                path.lineTo(4.8f, 19.8f);
                path.cubicTo(3.8f, 19.8f, 3, 19, 3, 18);
                path.lineTo(3, 9.2f);
                path.cubicTo(3, 8.3f, 3, 7.5f, 4, 7.5f);
                canvas.drawPath(path, paint);
                canvas.drawCircle(12, 13.5f, 3.2f, paint);
                break;
            case PLUS:
                canvas.drawLine(12, 5, 12, 19, paint);
                canvas.drawLine(5, 12, 19, 12, paint);
                break;
            case CALENDAR:
                canvas.drawRoundRect(new RectF(3.5f, 5.5f, 20.5f, 20.5f), 2, 2, paint);
                canvas.drawLine(3.5f, 9.5f, 20.5f, 9.5f, paint);
                canvas.drawLine(8, 3.5f, 8, 7.5f, paint);
                canvas.drawLine(16, 3.5f, 16, 7.5f, paint);
                break;
            case TRASH:
                canvas.drawLine(4.5f, 7, 19.5f, 7, paint);
                path.moveTo(7, 7);
                path.lineTo(8, 20);
                path.lineTo(16, 20);
                path.lineTo(17, 7);
                path.moveTo(9, 7);
                path.lineTo(9.8f, 4.5f);
                path.lineTo(14.2f, 4.5f);
                path.lineTo(15, 7);
                canvas.drawPath(path, paint);
                break;
            case CHECK:
                path.moveTo(4.5f, 12.5f);
                path.lineTo(9.5f, 17.5f);
                path.lineTo(19.5f, 6.5f);
                canvas.drawPath(path, paint);
                break;
            case CHEVRON:
                path.moveTo(9, 5.5f);
                path.lineTo(15.5f, 12);
                path.lineTo(9, 18.5f);
                canvas.drawPath(path, paint);
                break;
            case CLOSE:
                canvas.drawLine(6, 6, 18, 18, paint);
                canvas.drawLine(18, 6, 6, 18, paint);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }
}
