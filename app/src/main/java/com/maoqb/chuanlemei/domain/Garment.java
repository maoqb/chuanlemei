package com.maoqb.chuanlemei.domain;

import com.maoqb.chuanlemei.vision.ImageSignature;

public class Garment {
    public final String id;
    public final String name;
    public final String category;
    public final int color;
    public final String brand;
    public final String note;
    public final String photoPath;
    public final ImageSignature signature;
    public final String createdAt;
    public final String updatedAt;
    public final String archivedAt;

    public Garment(
            String id,
            String name,
            String category,
            int color,
            String brand,
            String note,
            String photoPath,
            ImageSignature signature,
            String createdAt,
            String updatedAt,
            String archivedAt
    ) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.color = color;
        this.brand = brand;
        this.note = note;
        this.photoPath = photoPath;
        this.signature = signature;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archivedAt = archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null && !archivedAt.isEmpty();
    }

    public Garment archived(String timestamp) {
        return new Garment(
                id,
                name,
                category,
                color,
                brand,
                note,
                photoPath,
                signature,
                createdAt,
                timestamp,
                timestamp
        );
    }
}
