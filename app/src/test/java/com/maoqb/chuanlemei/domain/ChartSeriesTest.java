package com.maoqb.chuanlemei.domain;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ChartSeriesTest {
    @Test
    public void groupsLongDailySeriesWithoutLosingCounts() {
        List<StatsCalculator.DailyCount> days = new ArrayList<>();
        for (int day = 1; day <= 30; day++) {
            days.add(new StatsCalculator.DailyCount(String.format("2026-08-%02d", day), 1));
        }

        List<ChartSeries.Point> points = ChartSeries.fromDailyCounts(days, 12);

        assertEquals(10, points.size());
        assertEquals(3, points.get(0).value);
        assertEquals("08/30", points.get(points.size() - 1).label);
        int total = 0;
        for (ChartSeries.Point point : points) {
            total += point.value;
        }
        assertEquals(30, total);
    }

    @Test
    public void handlesEmptyInput() {
        assertEquals(0, ChartSeries.fromDailyCounts(new ArrayList<>(), 12).size());
    }
}
