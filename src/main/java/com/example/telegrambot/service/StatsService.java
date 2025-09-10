package com.example.telegrambot.service;

import com.example.telegrambot.model.Meeting;
import com.example.telegrambot.model.MeetingType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
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
            // Сохраняем в локальный файл (как backup)
            List<Meeting> meetings = loadAllMeetings();
            meetings.add(meeting);
            objectMapper.writeValue(new File(dataFilePath), meetings);

            // Сохраняем в Google Sheets только встречи с офферами
            if (meeting.getMeetingType() == MeetingType.COMPLETED && !meeting.getOffers().isEmpty()) {
                googleSheetsService.saveMeetingToSheets(meeting.getUserId(), meeting.getOffers());
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

    public Map<String, Integer> getWeeklyOfferStats(Long userId) {
        // Читаем статистику только по завершенным встречам с офферами из Google Sheets
        return googleSheetsService.getWeeklyStatsFromSheets(userId);
    }

    public Map<String, Integer> getWeeklyRescheduleStats(Long userId) {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        List<Meeting> weeklyRescheduledMeetings = loadAllMeetings().stream()
                .filter(meeting -> meeting.getUserId() != null && meeting.getUserId().equals(userId))
                .filter(meeting -> meeting.getTimestamp().isAfter(weekAgo))
                .filter(meeting -> meeting.getMeetingType() == MeetingType.RESCHEDULED)
                .collect(Collectors.toList());

        Map<String, Integer> rescheduleStats = new HashMap<>();

        for (Meeting meeting : weeklyRescheduledMeetings) {
            String reason = meeting.getRescheduleReason();
            if (reason != null) {
                rescheduleStats.put(reason, rescheduleStats.getOrDefault(reason, 0) + 1);
            }
        }

        return rescheduleStats;
    }

    public List<Meeting> getWeeklyMeetingsWithComments(Long userId) {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        return loadAllMeetings().stream()
                .filter(meeting -> meeting.getUserId() != null && meeting.getUserId().equals(userId))
                .filter(meeting -> meeting.getTimestamp().isAfter(weekAgo))
                .filter(meeting -> (meeting.getComment() != null && !meeting.getComment().isEmpty()) ||
                        meeting.getMeetingType() == MeetingType.COMMENT)
                .collect(Collectors.toList());
    }

    public void clearUserStats(Long userId) {
        try {
            List<Meeting> allMeetings = loadAllMeetings();
            // Фильтруем - оставляем только встречи других пользователей
            List<Meeting> filteredMeetings = allMeetings.stream()
                    .filter(meeting -> !Objects.equals(meeting.getUserId(), userId))
                    .collect(Collectors.toList());

            // Сохраняем обновленный список
            objectMapper.writeValue(new File(dataFilePath), filteredMeetings);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при очистке данных пользователя", e);
        }
    }

    public String formatOfferStats(Map<String, Integer> stats) {
        if (stats.isEmpty()) {
            return "📊 Статистика продаж за неделю пуста.\nДобавьте встречи с офферами!";
        }

        StringBuilder sb = new StringBuilder("📊 Статистика продаж за неделю:\n\n");

        // Сортируем по убыванию количества
        stats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                });

        return sb.toString();
    }

    public String formatRescheduleStats(Map<String, Integer> stats) {
        if (stats.isEmpty()) {
            return "📅 Переносы за неделю отсутствуют.\nОтлично - встречи проходят по плану!";
        }

        StringBuilder sb = new StringBuilder("📅 Статистика переносов за неделю:\n\n");

        // Сортируем по убыванию количества
        stats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                });

        // Добавляем общий счетчик
        int totalReschedules = stats.values().stream().mapToInt(Integer::intValue).sum();
        sb.append("\nВсего переносов: ").append(totalReschedules);

        return sb.toString();
    }

    public String formatMeetingsWithComments(List<Meeting> meetings) {
        if (meetings.isEmpty()) {
            return "📝 Встречи с комментариями за неделю не найдены.";
        }

        StringBuilder sb = new StringBuilder("📝 Встречи с комментариями за неделю:\n\n");

        meetings.forEach(meeting -> {
            String dateTime = meeting.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"));
            String type = meeting.getMeetingType() == MeetingType.COMPLETED ? "Встреча" : "Перенос";

            sb.append("🕐 ").append(dateTime).append(" (").append(type).append(")\n");
            sb.append("💬 ").append(meeting.getComment()).append("\n\n");
        });

        return sb.toString();
    }
}