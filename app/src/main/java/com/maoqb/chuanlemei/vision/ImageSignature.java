package com.maoqb.chuanlemei.vision;

import android.graphics.Bitmap;

import java.util.Locale;

public class ImageSignature {
    private static final int CHANNEL_BINS = 4;
    private static final int HISTOGRAM_SIZE = CHANNEL_BINS * CHANNEL_BINS * CHANNEL_BINS;

    public final int averageRed;
    public final int averageGreen;
    public final int averageBlue;
    public final double[] histogram;

    public ImageSignature(int averageRed, int averageGreen, int averageBlue, double[] histogram) {
        this.averageRed = averageRed;
        this.averageGreen = averageGreen;
        this.averageBlue = averageBlue;
        this.histogram = histogram;
    }

    public static ImageSignature fromBitmap(Bitmap source) {
        int size = 72;
        Bitmap scaled = Bitmap.createScaledBitmap(source, size, size, true);
        int[] pixels = new int[size * size];
        scaled.getPixels(pixels, 0, size, 0, 0, size, size);
        if (scaled != source) {
            scaled.recycle();
        }
        return fromPixels(pixels, size, size);
    }

    public static ImageSignature fromPixels(int[] pixels, int width, int height) {
        double[] histogram = new double[HISTOGRAM_SIZE];
        double totalWeight = 0;
        double red = 0;
        double green = 0;
        double blue = 0;
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double maxDistance = Math.max(1, Math.hypot(centerX, centerY));

        for (int index = 0; index < pixels.length; index++) {
            int argb = pixels[index];
            int alpha = (argb >>> 24) & 0xff;
            if (alpha < 38) {
                continue;
            }
            int x = index % width;
            int y = index / width;
            double centerWeight = 1 - Math.hypot(x - centerX, y - centerY) / maxDistance;
            double weight = Math.max(0.35, centerWeight) * (alpha / 255.0);

            int r = (argb >>> 16) & 0xff;
            int g = (argb >>> 8) & 0xff;
            int b = argb & 0xff;
            histogram[colorBucket(r, g, b)] += weight;
            red += r * weight;
            green += g * weight;
            blue += b * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0) {
            return new ImageSignature(0, 0, 0, histogram);
        }

        for (int index = 0; index < histogram.length; index++) {
            histogram[index] = histogram[index] / totalWeight;
        }

        return new ImageSignature(
                (int) Math.round(red / totalWeight),
                (int) Math.round(green / totalWeight),
                (int) Math.round(blue / totalWeight),
                histogram
        );
    }

    public double compare(ImageSignature other) {
        double histogramSimilarity = 0;
        for (int index = 0; index < histogram.length; index++) {
            histogramSimilarity += Math.min(histogram[index], other.histogram[index]);
        }
        double colorDistance = Math.hypot(
                averageRed - other.averageRed,
                Math.hypot(averageGreen - other.averageGreen, averageBlue - other.averageBlue)
        );
        double colorSimilarity = Math.max(0, 1 - colorDistance / 441.67295593);
        double score = histogramSimilarity * 0.72 + colorSimilarity * 0.28;
        return Math.max(0, Math.min(1, round(score)));
    }

    public String serialize() {
        StringBuilder builder = new StringBuilder();
        builder.append(averageRed)
                .append(',')
                .append(averageGreen)
                .append(',')
                .append(averageBlue)
                .append(';');
        for (int index = 0; index < histogram.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.US, "%.8f", histogram[index]));
        }
        return builder.toString();
    }

    public static ImageSignature parse(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String[] parts = value.split(";", 2);
        if (parts.length != 2) {
            return null;
        }
        String[] rgb = parts[0].split(",");
        String[] bins = parts[1].split(",");
        if (rgb.length != 3 || bins.length != HISTOGRAM_SIZE) {
            return null;
        }
        double[] histogram = new double[HISTOGRAM_SIZE];
        for (int index = 0; index < bins.length; index++) {
            histogram[index] = Double.parseDouble(bins[index]);
        }
        return new ImageSignature(
                Integer.parseInt(rgb[0]),
                Integer.parseInt(rgb[1]),
                Integer.parseInt(rgb[2]),
                histogram
        );
    }

    private static int colorBucket(int red, int green, int blue) {
        int r = Math.min(CHANNEL_BINS - 1, (red * CHANNEL_BINS) / 256);
        int g = Math.min(CHANNEL_BINS - 1, (green * CHANNEL_BINS) / 256);
        int b = Math.min(CHANNEL_BINS - 1, (blue * CHANNEL_BINS) / 256);
        return r * CHANNEL_BINS * CHANNEL_BINS + g * CHANNEL_BINS + b;
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
