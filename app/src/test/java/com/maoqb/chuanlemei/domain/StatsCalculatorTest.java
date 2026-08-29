package com.maoqb.chuanlemei.domain;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class StatsCalculatorTest {
    @Test
    public void buildsPeriodGarmentAndOutfitStats() {
        List<Garment> garments = Arrays.asList(
                garment("top-1", "白T", Category.TOP),
                garment("bottom-1", "牛仔裤", Category.BOTTOM),
                garment("shoes-1", "白鞋", Category.SHOES)
        );
        List<WearRecord> records = Arrays.asList(
                record("wear-1", "2026-08-29", "top-1", "bottom-1", "shoes-1"),
                record("wear-2", "2026-08-01", "top-1", null, null)
        );

        StatsCalculator.DashboardStats stats = StatsCalculator.build(
                garments,
                records,
                new PeriodRange("2026-08-23", "2026-08-29")
        );

        assertEquals(1, stats.totalRecords);
        assertEquals(3, stats.totalGarmentWears);
        assertEquals(1, (int) stats.categoryCounts.get(Category.TOP));
        assertEquals(2, StatsCalculator.countForGarment(records, "top-1"));
        assertEquals(1, stats.outfitStats.get(0).count);
        assertEquals("2026-08-29", stats.garmentStats.get(0).lastWornAt);
    }

    private static Garment garment(String id, String name, String category) {
        return new Garment(
                id,
                name,
                category,
                0xffffffff,
                null,
                null,
                null,
                null,
                "2026-08-29T00:00:00Z",
                "2026-08-29T00:00:00Z",
                null
        );
    }

    private static WearRecord record(String id, String wornAt, String topId, String bottomId, String shoesId) {
        return new WearRecord(
                id,
                wornAt,
                wornAt + "T08:00:00Z",
                "/tmp/photo.jpg",
                topId,
                bottomId,
                shoesId,
                "",
                ""
        );
    }
}
