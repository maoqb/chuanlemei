package com.maoqb.chuanlemei.domain;

public final class Category {
    public static final String TOP = "top";
    public static final String BOTTOM = "bottom";
    public static final String SHOES = "shoes";
    public static final String[] ORDER = {TOP, BOTTOM, SHOES};

    private Category() {
    }

    public static String label(String category) {
        switch (category) {
            case TOP:
                return "上衣";
            case BOTTOM:
                return "裤子";
            case SHOES:
                return "鞋";
            default:
                return "未知";
        }
    }

    public static int accentColor(String category) {
        switch (category) {
            case TOP:
                return 0xff2f6f73;
            case BOTTOM:
                return 0xff715c9f;
            case SHOES:
                return 0xff9a5b38;
            default:
                return 0xff263b37;
        }
    }

    public static boolean isValid(String category) {
        return TOP.equals(category) || BOTTOM.equals(category) || SHOES.equals(category);
    }
}
