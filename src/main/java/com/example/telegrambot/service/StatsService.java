package com.example.telegrambot.service;

import com.example.telegrambot.model.Meeting;
import com.example.telegrambot.model.MeetingType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final ObjectMapper objectMapper;
    private final String dataFilePath = "meetings.json";
    private final GoogleSheetsService googleSheetsService;

    public StatsService(GoogleSheetsService googleSheetsService) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.googleSheetsService = googleSheetsService;
    }

    public void saveMeeting(Meeting meeting) {
        try {
            // backup в локальный файл
            List<Meeting> meetings = loadAllMeetings();
            meetings.add(meeting);
            objectMapper.writeValue(new File(dataFilePath), meetings);

            // запись в Google Sheets
            if (meeting.getMeetingType() == MeetingType.RESCHEDULED) {
                googleSheetsService.saveRescheduleToSheets(meeting.getUserId(), meeting.getRescheduleReason(), meeting.getComment());
            } else if (meeting.getMeetingType() == MeetingType.COMMENT) {
                googleSheetsService.saveCommentToSheets(meeting.getUserId(), meeting.getComment());
            } else {
                if (meeting.getOffers() != null && !meeting.getOffers().isEmpty()) {
                    googleSheetsService.saveMeetingToSheets(meeting.getUserId(), meeting.getOffers());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка сохранения встречи", e);
        }
    }

    public List<Meeting> loadAllMeetings() {
        try {
            File file = new File(dataFilePath);
            if (!file.exists()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(file, new TypeReference<List<Meeting>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    // ---- Еженедельные статистики ----

    /** Офферы: читаем только из Google Sheets (недельный лист пользователя) */
    public Map<String, Integer> getWeeklyOfferStats(Long userId) {
        return googleSheetsService.getWeeklyStatsFromSheets(userId);
    }

    /** Переносы: теперь читаем из Google Sheets, а не из локального JSON */
    public Map<String, Integer> getWeeklyRescheduleStats(Long userId) {
        return googleSheetsService.getWeeklyRescheduleStatsFromSheets(userId);
    }

    /** Комментарии: читаем из Google Sheets, а не из локального JSON */
    public List<Meeting> getWeeklyMeetingsWithComments(Long userId) {
        return googleSheetsService.getWeeklyCommentsFromSheets(userId);
    }

    public void clearUserStats(Long userId) {
        try {
            List<Meeting> allMeetings = loadAllMeetings();
            // оставляем только чужие встречи
            List<Meeting> filteredMeetings = allMeetings.stream()
                    .filter(meeting -> !Objects.equals(meeting.getUserId(), userId))
                    .collect(Collectors.toList());
            objectMapper.writeValue(new File(dataFilePath), filteredMeetings);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при очистке данных пользователя", e);
        }
    }

    // ---- Форматирование вывода ----

    public String formatOfferStats(Map<String, Integer> stats) {
        if (stats == null || stats.isEmpty()) {
            return "📊 Статистика продаж за неделю пуста.\nДобавьте встречи с офферами!";
        }
        StringBuilder sb = new StringBuilder("📊 Статистика продаж за неделю:\n\n");
        stats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n"));
        return sb.toString();
    }

    public String formatRescheduleStats(Map<String, Integer> stats) {
        if (stats == null || stats.isEmpty()) {
            return "📅 Переносы за неделю отсутствуют.\nОтлично — встречи проходят по плану!";
        }
        StringBuilder sb = new StringBuilder("📅 Статистика переносов за неделю:\n\n");
        stats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n"));
        return sb.toString();
    }

    public String formatMeetingsWithComments(List<Meeting> meetings) {
        if (meetings == null || meetings.isEmpty()) {
            return "📝 Комментариев за неделю нет.";
        }
        StringBuilder sb = new StringBuilder("📝 Встречи с комментариями за неделю:\n\n");
        meetings.forEach(meeting -> {
            String dateTime = meeting.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"));
            // для листа комментариев тип неизвестен, покажем как «Встреча»
            String type = (meeting.getMeetingType() == MeetingType.RESCHEDULED) ? "Перенос" : "Встреча";
            sb.append("🕐 ").append(dateTime).append(" (").append(type).append(")\n");
            sb.append("💬 ").append(meeting.getComment()).append("\n\n");
        });
        return sb.toString();
    }
}
