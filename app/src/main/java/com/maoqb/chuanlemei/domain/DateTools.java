package com.maoqb.chuanlemei.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DateTools {
    public static final String PERIOD_7_DAYS = "7d";
    public static final String PERIOD_30_DAYS = "30d";
    public static final String PERIOD_90_DAYS = "90d";
    public static final String PERIOD_YEAR = "year";
    public static final String PERIOD_ALL = "all";
    public static final String PERIOD_CUSTOM = "custom";

    private static final DateTimeFormatter READABLE = DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA);

    private DateTools() {
    }

    public static String today() {
        return format(LocalDate.now());
    }

    public static String nowIsoSecond() {
        return java.time.OffsetDateTime.now().withNano(0).toString();
    }

    public static boolean isToday(String value) {
        return today().equals(value);
    }

    public static String format(LocalDate date) {
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static LocalDate parse(String value) {
        return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static String readable(String value) {
        return parse(value).format(READABLE);
    }

    public static String shortDate(String value) {
        LocalDate date = parse(value);
        return String.format(Locale.CHINA, "%02d/%02d", date.getMonthValue(), date.getDayOfMonth());
    }

    public static PeriodRange rangeForPreset(String preset, String customStart, String customEnd) {
        LocalDate end = LocalDate.now();
        switch (preset) {
            case PERIOD_7_DAYS:
                return new PeriodRange(format(end.minusDays(6)), format(end));
            case PERIOD_30_DAYS:
                return new PeriodRange(format(end.minusDays(29)), format(end));
            case PERIOD_90_DAYS:
                return new PeriodRange(format(end.minusDays(89)), format(end));
            case PERIOD_YEAR:
                return new PeriodRange(format(end.withDayOfYear(1)), format(end));
            case PERIOD_ALL:
                return new PeriodRange("1970-01-01", format(end));
            case PERIOD_CUSTOM:
                return new PeriodRange(customStart, customEnd);
            default:
                return new PeriodRange(format(end.minusDays(29)), format(end));
        }
    }

    public static List<String> enumerateDates(PeriodRange range) {
        ArrayList<String> dates = new ArrayList<>();
        LocalDate cursor = parse(range.start);
        LocalDate end = parse(range.end);
        while (!cursor.isAfter(end)) {
            dates.add(format(cursor));
            cursor = cursor.plusDays(1);
        }
        return dates;
    }
}
