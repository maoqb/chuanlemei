package com.maoqb.chuanlemei.domain;

public class PeriodRange {
    public final String start;
    public final String end;

    public PeriodRange(String start, String end) {
        if (start.compareTo(end) <= 0) {
            this.start = start;
            this.end = end;
        } else {
            this.start = end;
            this.end = start;
        }
    }

    public boolean contains(String date) {
        return date.compareTo(start) >= 0 && date.compareTo(end) <= 0;
    }
}
