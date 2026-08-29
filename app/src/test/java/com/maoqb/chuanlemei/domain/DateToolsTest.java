package com.maoqb.chuanlemei.domain;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DateToolsTest {
    @Test
    public void formatsAndEnumeratesDates() {
        assertEquals("2026-08-29", DateTools.format(LocalDate.of(2026, 8, 29)));

        PeriodRange range = new PeriodRange("2026-08-29", "2026-08-27");
        assertEquals("2026-08-27", range.start);
        assertEquals("2026-08-29", range.end);
        assertEquals(3, DateTools.enumerateDates(range).size());
        assertTrue(range.contains("2026-08-28"));
    }
}
