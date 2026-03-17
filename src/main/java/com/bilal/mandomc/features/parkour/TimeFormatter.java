package com.bilal.mandomc.features.parkour;

public class TimeFormatter {

    public static String format(double seconds) {

        int minutes = (int) (seconds / 60);

        double remaining = seconds % 60;

        return String.format("%d:%04.1f", minutes, remaining);
    }

}