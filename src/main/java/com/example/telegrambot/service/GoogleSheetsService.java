package com.example.telegrambot.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.http.HttpCredentialsAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GoogleSheetsService {

    @Value("${google.sheets.spreadsheet.id}")
    private String spreadsheetId;

    private Sheets sheetsService;

    // Список всех возможных офферов для создания колонок
    private final List<String> ALL_OFFERS = Arrays.asList(
                    "КК", "НС", "ИНВЕСТИЦИИ", "ИНВЕСТ - АВТОСЛЕДОВАНИЕ",
            "ОБНОВЛЕНИЕ ДАННЫХ", "ЗАЩИТА КАРТЫ", "МП", "СИМ",
            "SIM MNP", "КРЕДИТ НАЛИЧНЫМИ", "ДЖУНИОР", "ДК",
            "PREMIUM", "PRIVATE", "PRO", "СОЦИАЛЬНЫЙ СЧЕТ",
            "КК+ОПТИМУМ", "ПРИВЕДИ ДРУГА", "УТИЛИЗАЦИЯ НС",
            "РЕФИНАНСИРОВАНИЕ", "ИНВЕСТИЦИИ УТИЛИЗАЦИЯ БС",
            "МП ИНВЕСТИЦИИ", "СЧЕТ ДЛЯ БИЗНЕСА", "БИЗНЕС КАРТА"
    );

    public GoogleSheetsService() {
        try {
            initializeSheetsService();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации Google Sheets", e);
        }
    }

    private void initializeSheetsService() throws IOException, GeneralSecurityException {
        InputStream credentialsStream = getClass().getResourceAsStream("/credentials.json");
        if (credentialsStream == null) {
            throw new RuntimeException("Файл credentials.json не найден в resources");
        }

        GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

        this.sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Telegram Bot Stats")
                .build();
    }

    public void saveMeetingToSheets(Long userId, List<String> offers) {
        try {
            String sheetName = generateSheetName(userId);

            // Проверяем существует ли лист, если нет - создаем
            if (!sheetExists(sheetName)) {
                createSheet(sheetName);
            }

            // Добавляем строку с данными встречи
            addMeetingRow(sheetName, offers);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка сохранения в Google Sheets", e);
        }
    }

    private String generateSheetName(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        // Находим понедельник текущей недели
        LocalDateTime monday = now.minusDays(now.getDayOfWeek().getValue() - 1);
        // Находим воскресенье
        LocalDateTime sunday = monday.plusDays(6);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d");
        String weekRange = monday.format(formatter) + "-" + sunday.format(formatter);

        return String.format("user%d_%s", userId, weekRange);
    }

    private boolean sheetExists(String sheetName) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        return spreadsheet.getSheets().stream()
                .anyMatch(sheet -> sheetName.equals(sheet.getProperties().getTitle()));
    }

    private void createSheet(String sheetName) throws IOException {
        // Создаем новый лист
        AddSheetRequest addSheetRequest = new AddSheetRequest()
                .setProperties(new SheetProperties().setTitle(sheetName));

        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(Collections.singletonList(new Request().setAddSheet(addSheetRequest)));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

        // Создаем заголовки
        createHeaders(sheetName);
    }

    private void createHeaders(String sheetName) throws IOException {
        List<Object> headers = new ArrayList<>();
        headers.add("Дата");
        headers.add("Время");
        headers.addAll(ALL_OFFERS);
        headers.add("Итого");

        ValueRange valueRange = new ValueRange()
                .setValues(Collections.singletonList(headers));

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A1", valueRange)
                .setValueInputOption("RAW")
                .execute();
    }

    private void addMeetingRow(String sheetName, List<String> offers) throws IOException {
        LocalDateTime now = LocalDateTime.now();

        List<Object> row = new ArrayList<>();
        row.add(now.format(DateTimeFormatter.ofPattern("dd.MM")));
        row.add(now.format(DateTimeFormatter.ofPattern("HH:mm")));

        // Подсчитываем количество каждого оффера
        Map<String, Integer> offerCounts = new HashMap<>();
        for (String offer : offers) {
            offerCounts.put(offer, offerCounts.getOrDefault(offer, 0) + 1);
        }

        // Добавляем количество для каждого типа оффера
        for (String offerType : ALL_OFFERS) {
            row.add(offerCounts.getOrDefault(offerType, 0));
        }

        // Добавляем общее количество
        row.add(offers.size());

        // Находим следующую пустую строку
        String range = sheetName + "!A:A";
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        int nextRow = response.getValues() != null ? response.getValues().size() + 1 : 2;

        // Добавляем строку
        ValueRange valueRange = new ValueRange()
                .setValues(Collections.singletonList(row));

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A" + nextRow, valueRange)
                .setValueInputOption("RAW")
                .execute();
    }

    public Map<String, Integer> getWeeklyStatsFromSheets(Long userId) {
        try {
            String sheetName = generateSheetName(userId);

            if (!sheetExists(sheetName)) {
                return new HashMap<>();
            }

            // Читаем все данные листа
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, sheetName + "!A:Z")
                    .execute();

            List<List<Object>> values = response.getValues();
            if (values == null || values.size() <= 1) {
                return new HashMap<>();
            }

            Map<String, Integer> stats = new HashMap<>();

            // Суммируем по колонкам (пропускаем заголовок)
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);

                // Начинаем с 3-й колонки (индекс 2), пропускаем дату и время
                for (int j = 2; j < Math.min(row.size() - 1, ALL_OFFERS.size() + 2); j++) {
                    String offerType = ALL_OFFERS.get(j - 2);
                    Object cellValue = j < row.size() ? row.get(j) : "0";

                    try {
                        int count = Integer.parseInt(cellValue.toString());
                        if (count > 0) {
                            stats.put(offerType, stats.getOrDefault(offerType, 0) + count);
                        }
                    } catch (NumberFormatException e) {
                        // Игнорируем некорректные значения
                    }
                }
            }

            return stats;

        } catch (Exception e) {
            throw new RuntimeException("Ошибка чтения из Google Sheets", e);
        }
    }
}