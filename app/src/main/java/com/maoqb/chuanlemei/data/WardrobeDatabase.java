package com.maoqb.chuanlemei.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.maoqb.chuanlemei.domain.Garment;
import com.maoqb.chuanlemei.domain.WearRecord;
import com.maoqb.chuanlemei.vision.ImageSignature;

import java.util.ArrayList;
import java.util.List;

public class WardrobeDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "chuanlemei.db";
    private static final int DB_VERSION = 1;

    public WardrobeDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE garments (" +
                "id TEXT PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "color INTEGER NOT NULL," +
                "brand TEXT," +
                "note TEXT," +
                "photo_path TEXT," +
                "signature TEXT," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL," +
                "archived_at TEXT" +
                ")");
        db.execSQL("CREATE TABLE wear_records (" +
                "id TEXT PRIMARY KEY," +
                "worn_at TEXT NOT NULL," +
                "captured_at TEXT NOT NULL," +
                "photo_path TEXT NOT NULL," +
                "top_id TEXT," +
                "bottom_id TEXT," +
                "shoes_id TEXT," +
                "recognition_summary TEXT," +
                "note TEXT" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported database upgrade from " + oldVersion + " to " + newVersion);
    }

    public void saveGarment(Garment garment) {
        ContentValues values = new ContentValues();
        values.put("id", garment.id);
        values.put("name", garment.name);
        values.put("category", garment.category);
        values.put("color", garment.color);
        values.put("brand", emptyToNull(garment.brand));
        values.put("note", emptyToNull(garment.note));
        values.put("photo_path", emptyToNull(garment.photoPath));
        values.put("signature", garment.signature == null ? null : garment.signature.serialize());
        values.put("created_at", garment.createdAt);
        values.put("updated_at", garment.updatedAt);
        values.put("archived_at", emptyToNull(garment.archivedAt));
        getWritableDatabase().insertWithOnConflict("garments", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void saveWearRecord(WearRecord record) {
        ContentValues values = new ContentValues();
        values.put("id", record.id);
        values.put("worn_at", record.wornAt);
        values.put("captured_at", record.capturedAt);
        values.put("photo_path", record.photoPath);
        values.put("top_id", emptyToNull(record.topId));
        values.put("bottom_id", emptyToNull(record.bottomId));
        values.put("shoes_id", emptyToNull(record.shoesId));
        values.put("recognition_summary", emptyToNull(record.recognitionSummary));
        values.put("note", emptyToNull(record.note));
        getWritableDatabase().insertWithOnConflict("wear_records", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deleteWearRecord(String id) {
        getWritableDatabase().delete("wear_records", "id = ?", new String[]{id});
    }

    public List<Garment> getGarments(boolean includeArchived) {
        ArrayList<Garment> result = new ArrayList<>();
        String selection = includeArchived ? null : "archived_at IS NULL";
        try (Cursor cursor = getReadableDatabase().query(
                "garments",
                null,
                selection,
                null,
                null,
                null,
                "created_at DESC"
        )) {
            while (cursor.moveToNext()) {
                result.add(readGarment(cursor));
            }
        }
        return result;
    }

    public List<WearRecord> getWearRecords() {
        ArrayList<WearRecord> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "wear_records",
                null,
                null,
                null,
                null,
                null,
                "worn_at DESC, captured_at DESC"
        )) {
            while (cursor.moveToNext()) {
                result.add(readWearRecord(cursor));
            }
        }
        return result;
    }

    private static Garment readGarment(Cursor cursor) {
        return new Garment(
                text(cursor, "id"),
                text(cursor, "name"),
                text(cursor, "category"),
                cursor.getInt(cursor.getColumnIndexOrThrow("color")),
                text(cursor, "brand"),
                text(cursor, "note"),
                text(cursor, "photo_path"),
                ImageSignature.parse(text(cursor, "signature")),
                text(cursor, "created_at"),
                text(cursor, "updated_at"),
                text(cursor, "archived_at")
        );
    }

    private static WearRecord readWearRecord(Cursor cursor) {
        return new WearRecord(
                text(cursor, "id"),
                text(cursor, "worn_at"),
                text(cursor, "captured_at"),
                text(cursor, "photo_path"),
                text(cursor, "top_id"),
                text(cursor, "bottom_id"),
                text(cursor, "shoes_id"),
                text(cursor, "recognition_summary"),
                text(cursor, "note")
        );
    }

    private static String text(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
