package com.maoqb.chuanlemei.vision;

import com.maoqb.chuanlemei.domain.Category;
import com.maoqb.chuanlemei.domain.Garment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GarmentRecognizer {
    private static final double AUTO_SELECT_THRESHOLD = 0.42;

    private GarmentRecognizer() {
    }

    public static List<RecognitionSlot> recognize(ImageSignature photoSignature, List<Garment> garments) {
        ArrayList<RecognitionSlot> slots = new ArrayList<>();
        for (String category : Category.ORDER) {
            ArrayList<RecognitionCandidate> candidates = new ArrayList<>();
            for (Garment garment : garments) {
                if (!category.equals(garment.category) || garment.isArchived() || garment.signature == null) {
                    continue;
                }
                candidates.add(new RecognitionCandidate(
                        garment.id,
                        garment.name,
                        category,
                        photoSignature.compare(garment.signature)
                ));
            }
            Collections.sort(candidates, (left, right) -> Double.compare(right.confidence, left.confidence));
            List<RecognitionCandidate> topCandidates = candidates.size() > 3
                    ? new ArrayList<>(candidates.subList(0, 3))
                    : candidates;
            RecognitionCandidate best = topCandidates.isEmpty() ? null : topCandidates.get(0);
            slots.add(new RecognitionSlot(
                    category,
                    best != null && best.confidence >= AUTO_SELECT_THRESHOLD ? best.garmentId : null,
                    best == null ? 0 : best.confidence,
                    topCandidates
            ));
        }
        return slots;
    }

    public static String summarize(List<RecognitionSlot> slots) {
        StringBuilder builder = new StringBuilder();
        for (RecognitionSlot slot : slots) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(Category.label(slot.category))
                    .append(':')
                    .append(slot.selectedGarmentId == null ? "未识别" : slot.selectedGarmentId)
                    .append('@')
                    .append(Math.round(slot.confidence * 100))
                    .append('%');
        }
        return builder.toString();
    }
}
