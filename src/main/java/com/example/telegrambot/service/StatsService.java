package com.example.telegrambot.service;

import com.example.telegrambot.model.Meeting;
import com.example.telegrambot.model.MeetingType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final ObjectMapper objectMapper;
    private final String dataFilePath = "meetings.json";
    private final GoogleSheetsService googleSheetsService;
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Locale RU = Locale.forLanguageTag("ru");

    private static LocalDate startOfWeek(LocalDate ref) {
        return ref.with(DayOfWeek.MONDAY);
    }
    private static String formatWeekRange(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);

        boolean sameMonth = weekStart.getMonth().equals(weekEnd.getMonth()) && weekStart.getYear() == weekEnd.getYear();
        if (sameMonth) {
            String month = weekStart.format(DateTimeFormatter.ofPattern("LLLL", RU)); // «сентября»
            return weekStart.getDayOfMonth() + "–" + weekEnd.getDayOfMonth() + " " + month;
        }

        boolean sameYear = weekStart.getYear() == weekEnd.getYear();
        String left = weekStart.format(DateTimeFormatter.ofPattern("d LLLL" + (sameYear ? "" : " yyyy"), RU));
        String right = weekEnd.format(DateTimeFormatter.ofPattern("d LLLL yyyy", RU));
        return left + " – " + right;
    }

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

            // запись в Google Sheets (временно отключено для тестирования)
            try {
                if (meeting.getMeetingType() == MeetingType.RESCHEDULED) {
                    googleSheetsService.saveRescheduleToSheets(meeting.getUserId(), meeting.getRescheduleReason(), meeting.getComment());
                } else if (meeting.getMeetingType() == MeetingType.COMMENT) {
                    googleSheetsService.saveCommentToSheets(meeting.getUserId(), meeting.getComment());
                } else {
                    if (meeting.getOffers() != null && !meeting.getOffers().isEmpty()) {
                        googleSheetsService.saveMeetingToSheets(meeting.getUserId(), meeting.getOffers(), meeting.getId());
                    }
                }
            } catch (Exception e) {
                // Игнорируем ошибки Google Sheets для тестирования
                System.out.println("Google Sheets недоступен: " + e.getMessage());
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
        LocalDate ws = startOfWeek(LocalDate.now(ZONE));
        String range = formatWeekRange(ws);

        if (stats == null || stats.isEmpty()) {
            return "📊 Статистика продаж за неделю (" + range + ") пуста.\nДобавьте встречи с офферами!";
        }
        StringBuilder sb = new StringBuilder("📊 Статистика продаж за неделю (" + range + "):\n\n");
        stats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n"));
        return sb.toString();
    }

    public String formatRescheduleStats(Map<String, Integer> stats) {
        LocalDate ws = startOfWeek(LocalDate.now(ZONE));
        String range = formatWeekRange(ws);

        if (stats == null || stats.isEmpty()) {
            return "📅 Переносы за неделю (" + range + ") отсутствуют.\nОтлично — встречи проходят по плану!";
        }
        StringBuilder sb = new StringBuilder("📅 Статистика переносов за неделю (" + range + "):\n\n");
        stats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n"));
        return sb.toString();
    }

    public String formatMeetingsWithComments(List<Meeting> meetings) {
        LocalDate ws = startOfWeek(LocalDate.now(ZONE));
        String range = formatWeekRange(ws);

        if (meetings == null || meetings.isEmpty()) {
            return "📝 Комментариев за неделю (" + range + ") нет.";
        }
        StringBuilder sb = new StringBuilder("📝 Встречи с комментариями за неделю (" + range + "):\n\n");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM HH:mm");
        meetings.forEach(meeting -> {
            String dateTime = meeting.getTimestamp().format(dtf);
            String type = (meeting.getMeetingType() == MeetingType.RESCHEDULED) ? "Перенос" : "Встреча";
            sb.append("🕐 ").append(dateTime).append(" (").append(type).append(")\n");
            sb.append("💬 ").append(meeting.getComment()).append("\n\n");
        });
        return sb.toString();
    }

    /**
     * Модифицирует исходный текст встречи, добавляя расшифровку офферов и дату встречи
     */
    public String getModifiedMeetingText(Long userId, String originalText) {
        List<Meeting> allMeetings = loadAllMeetings();
        
        // Ищем последнюю встречу пользователя с таким текстом
        Meeting targetMeeting = allMeetings.stream()
                .filter(meeting -> Objects.equals(meeting.getUserId(), userId))
                .filter(meeting -> meeting.getOriginalText() != null && 
                         meeting.getOriginalText().trim().equals(originalText.trim()))
                .max((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()))
                .orElse(null);
        
        if (targetMeeting == null) {
            return "❌ Встреча с таким текстом не найдена";
        }
        
        return modifyTextWithMeetingData(targetMeeting);
    }
    
    /**
     * Получает модифицированный текст встречи по ID активности
     */
    public String getModifiedMeetingTextById(String activityId) {
        // Сначала ищем в Google Sheets
        Meeting targetMeeting = googleSheetsService.findMeetingById(activityId);
        
        if (targetMeeting == null) {
            // Если не найдено в Google Sheets, ищем в локальном файле
            List<Meeting> allMeetings = loadAllMeetings();
            targetMeeting = allMeetings.stream()
                    .filter(meeting -> activityId.equals(meeting.getId()))
                    .findFirst()
                    .orElse(null);
        }
        
        if (targetMeeting == null) {
            return null; // Встреча не найдена
        }
        
        return modifyTextWithMeetingData(targetMeeting);
    }
    
    /**
     * Модифицирует текст встречи, добавляя расшифровку после "Мой вопрос:"
     */
    private String modifyTextWithMeetingData(Meeting meeting) {
        String originalText = meeting.getOriginalText();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        
        // Находим позицию "Мой вопрос:" (игнорируя регистр)
        String lowerText = originalText.toLowerCase();
        int questionIndex = lowerText.indexOf("мой вопрос:");
        
        if (questionIndex == -1) {
            return originalText; // Если не найдено, возвращаем исходный текст
        }
        
        // Извлекаем часть до "Мой вопрос:"
        String beforeQuestion = originalText.substring(0, questionIndex + "мой вопрос:".length());
        
        // Создаем модифицированную часть после "Мой вопрос:"
        StringBuilder modifiedPart = new StringBuilder();
        
        if (meeting.getMeetingType() == MeetingType.RESCHEDULED) {
            // Для переносов
            modifiedPart.append(" ПЕРЕНЕСЕНО - ");
            modifiedPart.append(meeting.getRescheduleReason());
            if (meeting.getComment() != null && !meeting.getComment().trim().isEmpty()) {
                modifiedPart.append(" (").append(meeting.getComment()).append(")");
            }
            modifiedPart.append(" [").append(meeting.getTimestamp().format(dateFormatter))
                       .append(" ").append(meeting.getTimestamp().format(timeFormatter)).append("]");
            
        } else if (meeting.getMeetingType() == MeetingType.COMMENT) {
            // Для комментариев
            modifiedPart.append(" КОММЕНТАРИЙ - ");
            modifiedPart.append(meeting.getComment());
            modifiedPart.append(" [").append(meeting.getTimestamp().format(dateFormatter))
                       .append(" ").append(meeting.getTimestamp().format(timeFormatter)).append("]");
            
        } else {
            // Для обычных встреч с офферами
            if (meeting.getOffers() != null && !meeting.getOffers().isEmpty()) {
                modifiedPart.append(" ");
                
                // Группируем офферы по количеству
                Map<String, Long> offerCounts = meeting.getOffers().stream()
                        .collect(Collectors.groupingBy(offer -> offer, Collectors.counting()));
                
                List<String> offerDescriptions = new ArrayList<>();
                for (Map.Entry<String, Long> entry : offerCounts.entrySet()) {
                    String offerName = entry.getKey();
                    Long count = entry.getValue();
                    if (count > 1) {
                        offerDescriptions.add(offerName + " (" + count + ")");
                    } else {
                        offerDescriptions.add(offerName);
                    }
                }
                
                modifiedPart.append(String.join(", ", offerDescriptions));
                modifiedPart.append(" [").append(meeting.getTimestamp().format(dateFormatter))
                           .append(" ").append(meeting.getTimestamp().format(timeFormatter)).append("]");
            }
        }
        
        return beforeQuestion + modifiedPart.toString();
    }
}
