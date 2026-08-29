package com.maoqb.chuanlemei.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.maoqb.chuanlemei.R;
import com.maoqb.chuanlemei.domain.Category;
import com.maoqb.chuanlemei.domain.DateTools;
import com.maoqb.chuanlemei.domain.Garment;
import com.maoqb.chuanlemei.domain.WearRecord;
import com.maoqb.chuanlemei.vision.ImageSignature;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DemoDataSeeder {
    private static final GarmentSpec[] GARMENTS = {
            new GarmentSpec("demo-top-white", "白色牛津纺衬衫", Category.TOP, 0xfff2f2ef, "通勤基础款", R.drawable.demo_white_shirt),
            new GarmentSpec("demo-top-green", "森林绿圆领 T 恤", Category.TOP, 0xff204b38, "周末休闲", R.drawable.demo_green_tee),
            new GarmentSpec("demo-bottom-navy", "藏蓝直筒西裤", Category.BOTTOM, 0xff202b45, "通勤基础款", R.drawable.demo_navy_trousers),
            new GarmentSpec("demo-bottom-jeans", "浅蓝直筒牛仔裤", Category.BOTTOM, 0xff9bb9ce, "四季款", R.drawable.demo_blue_jeans),
            new GarmentSpec("demo-shoes-white", "白色低帮运动鞋", Category.SHOES, 0xffeeeeea, "日常百搭", R.drawable.demo_white_sneakers),
            new GarmentSpec("demo-shoes-black", "黑色皮质乐福鞋", Category.SHOES, 0xff1b1c1c, "正式场合", R.drawable.demo_black_loafers)
    };

    private static final int[] DAY_OFFSETS = {
            0, 1, 1, 2, 4, 4, 4, 6, 7, 8, 8, 10,
            12, 12, 13, 15, 17, 17, 20, 22, 22, 25, 27, 29
    };

    private DemoDataSeeder() {
    }

    public static boolean seedIfEmpty(Context context, WardrobeDatabase database) {
        if (!database.getGarments(true).isEmpty() || !database.getWearRecords().isEmpty()) {
            return false;
        }

        try {
            List<Garment> garments = prepareGarments(context);
            for (Garment garment : garments) {
                database.saveGarment(garment);
            }
            for (WearRecord record : prepareRecords(garments)) {
                database.saveWearRecord(record);
            }
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private static List<Garment> prepareGarments(Context context) throws IOException {
        ArrayList<Garment> result = new ArrayList<>();
        for (int index = 0; index < GARMENTS.length; index++) {
            GarmentSpec spec = GARMENTS[index];
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), spec.drawableId);
            if (bitmap == null) {
                throw new IOException("Unable to decode demo garment image");
            }
            String photoPath = ImageStore.saveBitmap(context, bitmap, spec.id);
            ImageSignature signature = ImageSignature.fromBitmap(bitmap);
            bitmap.recycle();
            String createdAt = DateTools.format(LocalDate.now().minusDays(90L - index)) + "T09:00:00+08:00";
            result.add(new Garment(
                    spec.id,
                    spec.name,
                    spec.category,
                    spec.color,
                    "演示衣橱",
                    spec.note,
                    photoPath,
                    signature,
                    createdAt,
                    createdAt,
                    null
            ));
        }
        return result;
    }

    private static List<WearRecord> prepareRecords(List<Garment> garments) {
        Map<String, String> photos = new HashMap<>();
        for (Garment garment : garments) {
            photos.put(garment.id, garment.photoPath);
        }

        ArrayList<WearRecord> result = new ArrayList<>();
        for (int index = 0; index < DAY_OFFSETS.length; index++) {
            String topId = index % 3 == 0 ? "demo-top-green" : "demo-top-white";
            String bottomId = index % 7 == 0
                    ? null
                    : (index % 2 == 0 ? "demo-bottom-navy" : "demo-bottom-jeans");
            String shoesId = index % 4 == 0
                    ? null
                    : (index % 3 == 0 ? "demo-shoes-black" : "demo-shoes-white");
            String date = DateTools.format(LocalDate.now().minusDays(DAY_OFFSETS[index]));
            result.add(new WearRecord(
                    "demo-wear-" + index,
                    date,
                    date + "T08:" + String.format(Locale.US, "%02d", 10 + index) + ":00+08:00",
                    photos.get(topId),
                    topId,
                    bottomId,
                    shoesId,
                    "演示数据，衣物已确认",
                    index % 5 == 0 ? "通勤" : ""
            ));
        }
        return result;
    }

    private static final class GarmentSpec {
        final String id;
        final String name;
        final String category;
        final int color;
        final String note;
        final int drawableId;

        GarmentSpec(String id, String name, String category, int color, String note, int drawableId) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.color = color;
            this.note = note;
            this.drawableId = drawableId;
        }
    }
}
