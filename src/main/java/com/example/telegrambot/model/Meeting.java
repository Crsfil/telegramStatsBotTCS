package com.example.telegrambot.model;

import java.time.LocalDateTime;
import java.util.List;

public class Meeting {

    private LocalDateTime timestamp;
    private List<String> offers;
    private String originalText;
    private long userId;

    public Meeting() {}

    public Meeting(LocalDateTime timestamp, List<String> offers, String originalText, Long userId) {
        this.timestamp = timestamp;
        this.offers = offers;
        this.originalText = originalText;
        this.userId = userId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<String> getOffers() {
        return offers;
    }

    public void setOffers(List<String> offers) {
        this.offers = offers;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}