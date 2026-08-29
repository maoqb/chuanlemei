package com.maoqb.chuanlemei.vision;

import java.util.List;

public class RecognitionSlot {
    public final String category;
    public String selectedGarmentId;
    public double confidence;
    public final List<RecognitionCandidate> alternatives;

    public RecognitionSlot(
            String category,
            String selectedGarmentId,
            double confidence,
            List<RecognitionCandidate> alternatives
    ) {
        this.category = category;
        this.selectedGarmentId = selectedGarmentId;
        this.confidence = confidence;
        this.alternatives = alternatives;
    }
}
