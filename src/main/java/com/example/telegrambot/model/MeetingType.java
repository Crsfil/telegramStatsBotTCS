package com.example.telegrambot.model;

public enum MeetingType {
    COMPLETED("Проведена"),
    RESCHEDULED("Перенесена"),
    COMMENT("Комментарий");

    private final String displayName;

    MeetingType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}