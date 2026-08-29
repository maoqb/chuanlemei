package com.maoqb.chuanlemei.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChartSeries {
    private ChartSeries() {
    }

    public static List<Point> fromDailyCounts(List<StatsCalculator.DailyCount> days, int maxPoints) {
        if (days == null || days.isEmpty() || maxPoints <= 0) {
            return Collections.emptyList();
        }

        int bucketSize = Math.max(1, (int) Math.ceil(days.size() / (double) maxPoints));
        ArrayList<Point> result = new ArrayList<>();
        for (int start = 0; start < days.size(); start += bucketSize) {
            int end = Math.min(days.size(), start + bucketSize);
            int count = 0;
            for (int index = start; index < end; index++) {
                count += days.get(index).count;
            }
            StatsCalculator.DailyCount last = days.get(end - 1);
            result.add(new Point(DateTools.shortDate(last.date), count));
        }
        return result;
    }

    public static final class Point {
        public final String label;
        public final int value;

        public Point(String label, int value) {
            this.label = label;
            this.value = value;
        }
    }
}
