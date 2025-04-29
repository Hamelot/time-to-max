package com.timetomax;

public enum TrackingInterval {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month");

    private final String name;

    TrackingInterval(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}