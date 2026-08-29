package com.maoqb.chuanlemei.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ImageStore {
    private ImageStore() {
    }

    public static Bitmap loadBitmap(String path, int maxSide) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide);
        Bitmap decoded = BitmapFactory.decodeFile(path, options);
        return scaleDown(decoded, maxSide);
    }

    public static Bitmap loadBitmap(Context context, Uri uri, int maxSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide);
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            Bitmap decoded = BitmapFactory.decodeStream(input, null, options);
            return scaleDown(decoded, maxSide);
        }
    }

    public static String saveBitmap(Context context, Bitmap bitmap, String prefix) throws IOException {
        File directory = new File(context.getFilesDir(), "photos");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建照片目录");
        }
        File output = new File(directory, prefix + "_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)) {
                throw new IOException("照片保存失败");
            }
        }
        return output.getAbsolutePath();
    }

    private static int sampleSize(int width, int height, int maxSide) {
        int sample = 1;
        int largest = Math.max(width, height);
        while (largest / sample > maxSide * 2) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }

    private static Bitmap scaleDown(Bitmap source, int maxSide) {
        if (source == null) {
            return null;
        }
        int largest = Math.max(source.getWidth(), source.getHeight());
        if (largest <= maxSide) {
            return source;
        }
        float scale = maxSide / (float) largest;
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, width, height, true);
        source.recycle();
        return scaled;
    }
}
