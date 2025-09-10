package com.example.telegrambot.service;

import com.example.telegrambot.model.OfferType;
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

    // Используем enum для получения списка офферов - единый источник истины
    private final List<String> ALL_OFFERS = OfferType.getAllDisplayNames();

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
    private void createRescheduleSheet(String sheetName) throws IOException {
        // Создаем новый лист
        AddSheetRequest addSheetRequest = new AddSheetRequest()
                .setProperties(new SheetProperties().setTitle(sheetName));

        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(Collections.singletonList(new Request().setAddSheet(addSheetRequest)));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

        // Создаем заголовки для переносов
        List<Object> headers = Arrays.asList("Дата", "Время", "Причина", "Комментарий");
        ValueRange valueRange = new ValueRange().setValues(Collections.singletonList(headers));

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A1", valueRange)
                .setValueInputOption("RAW")
                .execute();
    }

    private void addRescheduleRow(String sheetName, String reason, String comment) throws IOException {
        LocalDateTime now = LocalDateTime.now();

        List<Object> row = Arrays.asList(
                now.format(DateTimeFormatter.ofPattern("dd.MM")),
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                reason,
                comment
        );

        // Находим следующую пустую строку
        addRowToSheet(sheetName, row);
    }

    private void addRowToSheet(String sheetName, List<Object> row) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, sheetName + "!A:A")
                .execute();

        int nextRow = response.getValues() != null ? response.getValues().size() + 1 : 2;

        ValueRange valueRange = new ValueRange().setValues(Collections.singletonList(row));
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A" + nextRow, valueRange)
                .setValueInputOption("RAW")
                .execute();
    }


    private void createCommentSheet(String sheetName) throws IOException {
        // Создаем новый лист
        AddSheetRequest addSheetRequest = new AddSheetRequest()
                .setProperties(new SheetProperties().setTitle(sheetName));

        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(Collections.singletonList(new Request().setAddSheet(addSheetRequest)));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

        // Создаем заголовки для комментариев
        List<Object> headers = Arrays.asList("Дата", "Время", "Комментарий");
        ValueRange valueRange = new ValueRange().setValues(Collections.singletonList(headers));

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A1", valueRange)
                .setValueInputOption("RAW")
                .execute();
    }

    private void addCommentRow(String sheetName, String comment) throws IOException {
        LocalDateTime now = LocalDateTime.now();

        List<Object> row = Arrays.asList(
                now.format(DateTimeFormatter.ofPattern("dd.MM")),
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                comment
        );

        // Находим следующую пустую строку
        addRowToSheet(sheetName, row);
    }
    public void saveRescheduleToSheets(Long userId, String reason, String comment) {
        try {
            String sheetName = generateRescheduleSheetName(userId);
            if (!sheetExists(sheetName)) {
                createRescheduleSheet(sheetName);
            }
            addRescheduleRow(sheetName, reason, comment);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сохранения переноса", e);
        }
    }

    public void saveCommentToSheets(Long userId, String comment) {
        try {
            String sheetName = generateCommentSheetName(userId);
            if (!sheetExists(sheetName)) {
                createCommentSheet(sheetName);
            }
            addCommentRow(sheetName, comment);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сохранения комментария", e);
        }
    }

    private String generateRescheduleSheetName(Long userId) {
        return generateSheetName(userId) + "_Переносы";
    }

    private String generateCommentSheetName(Long userId) {
        return generateSheetName(userId) + "_Комментарии";
    }
    /**
     * Сохраняет встречу с офферами в Google Sheets
     * Переносы сохраняются только в локальный JSON файл
     */
    public void saveMeetingToSheets(Long userId, List<String> offers) {
        try {
            if (offers == null || offers.isEmpty()) {
                return; // Не сохраняем пустые встречи
            }

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

    /**
     * Генерирует название листа для пользователя на текущую неделю
     * Формат: user{userId}_{месяц} {день}-{день}
     */
    private String generateSheetName(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        // Находим понедельник текущей недели
        LocalDateTime monday = now.minusDays(now.getDayOfWeek().getValue() - 1);
        // Находим воскресенье
        LocalDateTime sunday = monday.plusDays(6);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d", Locale.forLanguageTag("ru"));
        String weekRange = monday.format(formatter) + "-" + sunday.format(formatter);

        return String.format("user%d_%s", userId, weekRange);
    }

    /**
     * Проверяет существование листа с заданным именем
     */
    private boolean sheetExists(String sheetName) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        return spreadsheet.getSheets().stream()
                .anyMatch(sheet -> sheetName.equals(sheet.getProperties().getTitle()));
    }

    /**
     * Создает новый лист с заданным именем
     */
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

    /**
     * Создает заголовки для нового листа
     */
    private void createHeaders(String sheetName) throws IOException {
        List<Object> headers = new ArrayList<>();
        headers.add("Дата");
        headers.add("Время");

        // Добавляем все офферы из enum
        headers.addAll(ALL_OFFERS);
        headers.add("Итого");

        ValueRange valueRange = new ValueRange()
                .setValues(Collections.singletonList(headers));

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A1", valueRange)
                .setValueInputOption("RAW")
                .execute();
    }

    /**
     * Добавляет строку с данными встречи в лист
     */
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

        // Добавляем количество для каждого типа оффера согласно enum
        for (String offerType : ALL_OFFERS) {
            row.add(offerCounts.getOrDefault(offerType, 0));
        }

        // Добавляем общее количество офферов в встрече
        row.add(offers.size());

        // Находим следующую пустую строку
        String range = sheetName + "!A:A";
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        int nextRow = response.getValues() != null ? response.getValues().size() + 1 : 2;

        // Добавляем строку с данными
        ValueRange valueRange = new ValueRange()
                .setValues(Collections.singletonList(row));

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A" + nextRow, valueRange)
                .setValueInputOption("RAW")
                .execute();
    }

    /**
     * Получает недельную статистику по офферам из Google Sheets
     * Используется для команды /statsOffers
     */
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
                return new HashMap<>(); // Нет данных или только заголовки
            }

            return calculateOfferStatistics(values);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка чтения из Google Sheets", e);
        }
    }

    private Map<String, Integer> calculateOfferStatistics(List<List<Object>> values) {
        Map<String, Integer> stats = new HashMap<>();

        // Суммируем по колонкам (пропускаем заголовок - строка 0)
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);

            // Начинаем с 3-й колонки (индекс 2), пропускаем дату и время
            // Заканчиваем до колонки "Итого"
            for (int j = 2; j < Math.min(row.size() - 1, ALL_OFFERS.size() + 2); j++) {
                if (j - 2 >= ALL_OFFERS.size()) break; // Защита от выхода за границы

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
    }

    /**
     * Удаляет листы пользователя (для команды /reset)
     */
    private void deleteSheetIfExists(String sheetName) {
        try {
            if (sheetExists(sheetName)) {
                // Найти ID листа
                Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
                Integer sheetId = spreadsheet.getSheets().stream()
                        .filter(sheet -> sheetName.equals(sheet.getProperties().getTitle()))
                        .map(sheet -> sheet.getProperties().getSheetId())
                        .findFirst()
                        .orElse(null);

                if (sheetId != null) {
                    // Удалить лист
                    DeleteSheetRequest deleteRequest = new DeleteSheetRequest().setSheetId(sheetId);
                    BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                            .setRequests(Collections.singletonList(new Request().setDeleteSheet(deleteRequest)));
                    sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
                }
            }
        } catch (Exception e) {
            // Не критично если лист не удалился - пользователь может удалить вручную
            System.err.println("Предупреждение: не удалось удалить лист " + sheetName + ": " + e.getMessage());
        }
    }
}