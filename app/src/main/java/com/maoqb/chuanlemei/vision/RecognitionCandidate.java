package com.maoqb.chuanlemei.vision;

public class RecognitionCandidate {
    public final String garmentId;
    public final String garmentName;
    public final String category;
    public final double confidence;

    public RecognitionCandidate(String garmentId, String garmentName, String category, double confidence) {
        this.garmentId = garmentId;
        this.garmentName = garmentName;
        this.category = category;
        this.confidence = confidence;
    }
}
