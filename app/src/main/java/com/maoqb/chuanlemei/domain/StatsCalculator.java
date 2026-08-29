package com.maoqb.chuanlemei.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StatsCalculator {
    private StatsCalculator() {
    }

    public static DashboardStats build(List<Garment> garments, List<WearRecord> records, PeriodRange range) {
        HashMap<String, Garment> garmentsById = new HashMap<>();
        for (Garment garment : garments) {
            garmentsById.put(garment.id, garment);
        }

        LinkedHashMap<String, Integer> categoryCounts = new LinkedHashMap<>();
        for (String category : Category.ORDER) {
            categoryCounts.put(category, 0);
        }

        LinkedHashMap<String, DailyCount> dailyCounts = new LinkedHashMap<>();
        for (String date : DateTools.enumerateDates(range)) {
            dailyCounts.put(date, new DailyCount(date, 0));
        }

        HashMap<String, MutableGarmentCount> perGarment = new HashMap<>();
        for (Garment garment : garments) {
            perGarment.put(garment.id, new MutableGarmentCount());
        }

        HashMap<String, OutfitStat> outfits = new HashMap<>();
        int totalRecords = 0;

        for (WearRecord record : records) {
            for (String id : record.garmentIds()) {
                MutableGarmentCount count = perGarment.get(id);
                if (count == null) {
                    count = new MutableGarmentCount();
                    perGarment.put(id, count);
                }
                count.total += 1;
                if (count.lastWornAt == null || record.wornAt.compareTo(count.lastWornAt) > 0) {
                    count.lastWornAt = record.wornAt;
                }
                if (range.contains(record.wornAt)) {
                    count.inRange += 1;
                }
            }

            if (!range.contains(record.wornAt)) {
                continue;
            }

            totalRecords += 1;
            DailyCount daily = dailyCounts.get(record.wornAt);
            if (daily != null) {
                daily.count += 1;
            }

            for (String category : Category.ORDER) {
                String garmentId = record.garmentIdForCategory(category);
                if (garmentId != null && garmentsById.containsKey(garmentId)) {
                    categoryCounts.put(category, categoryCounts.get(category) + 1);
                }
            }

            String outfitKey = outfitKey(record);
            if (!outfitKey.isEmpty()) {
                OutfitStat outfit = outfits.get(outfitKey);
                if (outfit == null) {
                    outfit = new OutfitStat(outfitKey, record.topId, record.bottomId, record.shoesId, 0, null);
                    outfits.put(outfitKey, outfit);
                }
                outfit.count += 1;
                if (outfit.lastWornAt == null || record.wornAt.compareTo(outfit.lastWornAt) > 0) {
                    outfit.lastWornAt = record.wornAt;
                }
            }
        }

        ArrayList<GarmentStat> garmentStats = new ArrayList<>();
        int activeGarments = 0;
        for (Garment garment : garments) {
            if (!garment.isArchived()) {
                activeGarments += 1;
            }
            MutableGarmentCount count = perGarment.get(garment.id);
            if (count == null) {
                count = new MutableGarmentCount();
            }
            garmentStats.add(new GarmentStat(garment, count.total, count.inRange, count.lastWornAt));
        }
        Collections.sort(garmentStats, (left, right) -> {
            int rangeCompare = Integer.compare(right.rangeCount, left.rangeCount);
            if (rangeCompare != 0) {
                return rangeCompare;
            }
            return Integer.compare(right.totalCount, left.totalCount);
        });

        ArrayList<OutfitStat> outfitStats = new ArrayList<>(outfits.values());
        Collections.sort(outfitStats, (left, right) -> Integer.compare(right.count, left.count));

        int garmentWears = 0;
        for (Integer value : categoryCounts.values()) {
            garmentWears += value;
        }

        return new DashboardStats(
                totalRecords,
                garmentWears,
                activeGarments,
                categoryCounts,
                new ArrayList<>(dailyCounts.values()),
                garmentStats,
                outfitStats
        );
    }

    public static int countForGarment(List<WearRecord> records, String garmentId) {
        int count = 0;
        for (WearRecord record : records) {
            if (record.containsGarment(garmentId)) {
                count += 1;
            }
        }
        return count;
    }

    public static List<WearRecord> recordsForGarment(List<WearRecord> records, String garmentId) {
        ArrayList<WearRecord> result = new ArrayList<>();
        for (WearRecord record : records) {
            if (record.containsGarment(garmentId)) {
                result.add(record);
            }
        }
        Collections.sort(result, (left, right) -> right.wornAt.compareTo(left.wornAt));
        return result;
    }

    private static String outfitKey(WearRecord record) {
        String top = record.topId == null ? "" : record.topId;
        String bottom = record.bottomId == null ? "" : record.bottomId;
        String shoes = record.shoesId == null ? "" : record.shoesId;
        String key = top + "|" + bottom + "|" + shoes;
        return key.equals("||") ? "" : key;
    }

    private static class MutableGarmentCount {
        int total;
        int inRange;
        String lastWornAt;
    }

    public static class DashboardStats {
        public final int totalRecords;
        public final int totalGarmentWears;
        public final int activeGarments;
        public final Map<String, Integer> categoryCounts;
        public final List<DailyCount> dailyCounts;
        public final List<GarmentStat> garmentStats;
        public final List<OutfitStat> outfitStats;

        DashboardStats(
                int totalRecords,
                int totalGarmentWears,
                int activeGarments,
                Map<String, Integer> categoryCounts,
                List<DailyCount> dailyCounts,
                List<GarmentStat> garmentStats,
                List<OutfitStat> outfitStats
        ) {
            this.totalRecords = totalRecords;
            this.totalGarmentWears = totalGarmentWears;
            this.activeGarments = activeGarments;
            this.categoryCounts = categoryCounts;
            this.dailyCounts = dailyCounts;
            this.garmentStats = garmentStats;
            this.outfitStats = outfitStats;
        }
    }

    public static class DailyCount {
        public final String date;
        public int count;

        DailyCount(String date, int count) {
            this.date = date;
            this.count = count;
        }
    }

    public static class GarmentStat {
        public final Garment garment;
        public final int totalCount;
        public final int rangeCount;
        public final String lastWornAt;

        GarmentStat(Garment garment, int totalCount, int rangeCount, String lastWornAt) {
            this.garment = garment;
            this.totalCount = totalCount;
            this.rangeCount = rangeCount;
            this.lastWornAt = lastWornAt;
        }
    }

    public static class OutfitStat {
        public final String key;
        public final String topId;
        public final String bottomId;
        public final String shoesId;
        public int count;
        public String lastWornAt;

        OutfitStat(String key, String topId, String bottomId, String shoesId, int count, String lastWornAt) {
            this.key = key;
            this.topId = topId;
            this.bottomId = bottomId;
            this.shoesId = shoesId;
            this.count = count;
            this.lastWornAt = lastWornAt;
        }
    }
}
