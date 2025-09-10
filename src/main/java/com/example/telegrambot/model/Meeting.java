package com.example.telegrambot.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Meeting {
    private String id; // Уникальный ID встречи
    private LocalDateTime timestamp;
    private List<String> offers;
    private String originalText;
    private Long userId;

    // Новые поля для переносов
    private String rescheduleReason; // причина переноса
    private String comment; // комментарий в свободной форме
    private MeetingType meetingType; // COMPLETED или RESCHEDULED

    public Meeting() {}

    // Конструктор для обычных встреч с офферами
    public Meeting(LocalDateTime timestamp, List<String> offers, String originalText, Long userId) {
        this.id = extractActivityId(originalText);
        this.timestamp = timestamp;
        this.offers = offers;
        this.originalText = originalText;
        this.userId = userId;
        this.meetingType = MeetingType.COMPLETED;
    }

    // Конструктор для переносов
    public Meeting(LocalDateTime timestamp, String originalText, Long userId,
                   String rescheduleReason, String comment) {
        this.id = extractActivityId(originalText);
        this.timestamp = timestamp;
        this.offers = new ArrayList<>(); // Пустой список офферов для переносов
        this.originalText = originalText;
        this.userId = userId;
        this.rescheduleReason = rescheduleReason;
        this.comment = comment;
        this.meetingType = MeetingType.RESCHEDULED;
    }
    public Meeting(LocalDateTime timestamp, String originalText, Long userId, String comment) {
        this.id = extractActivityId(originalText);
        this.timestamp = timestamp;
        this.offers = new ArrayList<>();
        this.originalText = originalText;
        this.userId = userId;
        this.comment = comment;
        this.meetingType = MeetingType.COMMENT;
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

    public String getRescheduleReason() {
        return rescheduleReason;
    }

    public void setRescheduleReason(String rescheduleReason) {
        this.rescheduleReason = rescheduleReason;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public MeetingType getMeetingType() {
        return meetingType;
    }

    public void setMeetingType(MeetingType meetingType) {
        this.meetingType = meetingType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Извлекает ID активности из текста встречи
     * Ищет строку вида "Id активности - XXXXX" или "ID активности - XXXXX"
     */
    private String extractActivityId(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            // Ищем строки с ID активности (разные варианты написания)
            if (trimmedLine.toLowerCase().contains("id активности") || 
                trimmedLine.toLowerCase().contains("id активности")) {
                String[] parts = trimmedLine.split("-");
                if (parts.length >= 2) {
                    return parts[1].trim();
                }
            }
        }
        return null;
    }
}