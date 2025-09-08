package com.example.telegrambot.service;

import com.example.telegrambot.model.Meeting;
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

    public StatsService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void saveMeeting(Meeting meeting) {
        try {
            List<Meeting> meetings = loadAllMeetings();
            meetings.add(meeting);

            objectMapper.writeValue(new File(dataFilePath), meetings);
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

    public Map<String, Integer> getWeeklyStats() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        List<Meeting> weeklyMeetings = loadAllMeetings().stream()
                .filter(meeting -> meeting.getTimestamp().isAfter(weekAgo))
                .collect(Collectors.toList());

        Map<String, Integer> stats = new HashMap<>();

        for (Meeting meeting : weeklyMeetings) {
            for (String offer : meeting.getOffers()) {
                stats.put(offer, stats.getOrDefault(offer, 0) + 1);
            }
        }

        return stats;
    }

    public String formatStats(Map<String, Integer> stats) {
        if (stats.isEmpty()) {
            return "📊 Статистика за неделю пуста.\nДобавьте встречи с 'Мой вопрос:'";
        }

        StringBuilder sb = new StringBuilder("📊 Статистика за неделю:\n\n");

        // Сортируем по убыванию количества
        stats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                });

        return sb.toString();
    }
}