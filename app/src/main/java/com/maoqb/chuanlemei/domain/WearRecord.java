package com.maoqb.chuanlemei.domain;

import java.util.ArrayList;
import java.util.List;

public class WearRecord {
    public final String id;
    public final String wornAt;
    public final String capturedAt;
    public final String photoPath;
    public final String topId;
    public final String bottomId;
    public final String shoesId;
    public final String recognitionSummary;
    public final String note;

    public WearRecord(
            String id,
            String wornAt,
            String capturedAt,
            String photoPath,
            String topId,
            String bottomId,
            String shoesId,
            String recognitionSummary,
            String note
    ) {
        this.id = id;
        this.wornAt = wornAt;
        this.capturedAt = capturedAt;
        this.photoPath = photoPath;
        this.topId = topId;
        this.bottomId = bottomId;
        this.shoesId = shoesId;
        this.recognitionSummary = recognitionSummary;
        this.note = note;
    }

    public String garmentIdForCategory(String category) {
        switch (category) {
            case Category.TOP:
                return topId;
            case Category.BOTTOM:
                return bottomId;
            case Category.SHOES:
                return shoesId;
            default:
                return null;
        }
    }

    public List<String> garmentIds() {
        ArrayList<String> ids = new ArrayList<>();
        addIfPresent(ids, topId);
        addIfPresent(ids, bottomId);
        addIfPresent(ids, shoesId);
        return ids;
    }

    public boolean containsGarment(String garmentId) {
        return garmentId != null && garmentIds().contains(garmentId);
    }

    private static void addIfPresent(List<String> ids, String id) {
        if (id != null && !id.isEmpty()) {
            ids.add(id);
        }
    }
}
