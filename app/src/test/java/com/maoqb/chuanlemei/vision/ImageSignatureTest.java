package com.maoqb.chuanlemei.vision;

import com.maoqb.chuanlemei.domain.Category;
import com.maoqb.chuanlemei.domain.Garment;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ImageSignatureTest {
    @Test
    public void comparesSimilarColorsHigherThanDifferentColors() {
        ImageSignature red = solidSignature(220, 34, 34);
        ImageSignature redAgain = solidSignature(220, 34, 34);
        ImageSignature blue = solidSignature(32, 78, 180);

        assertTrue(red.compare(redAgain) > 0.99);
        assertTrue(red.compare(blue) < 0.45);
    }

    @Test
    public void recognizesBestGarmentByCategory() {
        ImageSignature red = solidSignature(220, 34, 34);
        ImageSignature blue = solidSignature(32, 78, 180);
        List<Garment> garments = Arrays.asList(
                garment("red-top", "红衬衫", Category.TOP, red),
                garment("blue-top", "蓝衬衫", Category.TOP, blue),
                garment("blue-shoes", "蓝鞋", Category.SHOES, blue)
        );

        List<RecognitionSlot> slots = GarmentRecognizer.recognize(red, garments);

        assertEquals("red-top", find(slots, Category.TOP).selectedGarmentId);
        assertNull(find(slots, Category.SHOES).selectedGarmentId);
    }

    private static RecognitionSlot find(List<RecognitionSlot> slots, String category) {
        for (RecognitionSlot slot : slots) {
            if (category.equals(slot.category)) {
                return slot;
            }
        }
        throw new AssertionError("missing slot " + category);
    }

    private static ImageSignature solidSignature(int red, int green, int blue) {
        int[] pixels = new int[64];
        int argb = 0xff000000 | (red << 16) | (green << 8) | blue;
        Arrays.fill(pixels, argb);
        return ImageSignature.fromPixels(pixels, 8, 8);
    }

    private static Garment garment(String id, String name, String category, ImageSignature signature) {
        return new Garment(
                id,
                name,
                category,
                0xff000000,
                null,
                null,
                null,
                signature,
                "2026-08-29T00:00:00Z",
                "2026-08-29T00:00:00Z",
                null
        );
    }
}
