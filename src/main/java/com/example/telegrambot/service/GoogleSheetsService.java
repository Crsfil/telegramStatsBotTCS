package com.example.telegrambot.service;

import com.example.telegrambot.model.Meeting;
import com.example.telegrambot.model.MeetingType;
import com.example.telegrambot.model.OfferType;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Работа с Google Sheets: недельные листы для офферов, отдельные недельные листы для переносов и комментариев.
 *
 * Именование:
 *   Базовый недельный лист (офферы): user{userId}_{MMM d}-{MMM d}
 *   Переносы:   user{userId}_{MMM d}-{MMM d}_Переносы
 *   Комментарии: user{userId}_{MMM d}-{MMM d}_Комментарии
 */
@Service
public class GoogleSheetsService {

    private final String spreadsheetId;
    private Sheets sheetsService;

    public GoogleSheetsService(@Value("${google.sheets.spreadsheet.id}") String spreadsheetId) {
        this.spreadsheetId = spreadsheetId;
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
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName("TelegramBot").build();
    }

    // ---------- Публичные методы, используемые сервисами ----------

    /** Запись офферов в недельный лист */
    public void saveMeetingToSheets(Long userId, List<String> offers, String activityId) {
        try {
            String sheetName = generateSheetName(userId);
            if (!sheetExists(sheetName)) {
                createOfferSheet(sheetName);
            }
            addMeetingRow(sheetName, offers, activityId);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сохранения офферов в Google Sheets", e);
        }
    }

    /** Поиск встречи по ID активности */
    public Meeting findMeetingById(String activityId) {
        try {
            // Получаем список всех листов
            Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
            List<Sheet> sheets = spreadsheet.getSheets();
            
            for (Sheet sheet : sheets) {
                String sheetName = sheet.getProperties().getTitle();
                
                // Пропускаем листы с переносами и комментариями
                if (sheetName.contains("_Переносы") || sheetName.contains("_Комментарии")) {
                    continue;
                }
                
                // Читаем данные листа
                ValueRange vr = sheetsService.spreadsheets().values()
                        .get(spreadsheetId, sheetName + "!A:ZZ").execute();
                
                List<List<Object>> rows = vr.getValues();
                if (rows == null || rows.isEmpty()) continue;
                
                // Ищем строку с нужным ID активности (колонка C)
                for (int i = 1; i < rows.size(); i++) { // начиная со второй строки (пропускаем заголовки)
                    List<Object> row = rows.get(i);
                    if (row.size() >= 3 && activityId.equals(String.valueOf(row.get(2)))) {
                        // Найдена встреча, создаем объект Meeting
                        Meeting meeting = new Meeting();
                        meeting.setId(activityId);
                        meeting.setTimestamp(LocalDateTime.now()); // Время встречи
                        
                        // Извлекаем офферы из строки
                        List<String> offers = new ArrayList<>();
                        for (int j = 3; j < row.size(); j++) {
                            if ("1".equals(String.valueOf(row.get(j)))) {
                                // Получаем название оффера из заголовка
                                if (rows.get(0).size() > j) {
                                    offers.add(String.valueOf(rows.get(0).get(j)));
                                }
                            }
                        }
                        meeting.setOffers(offers);
                        meeting.setMeetingType(MeetingType.COMPLETED);
                        meeting.setOriginalText("Мой вопрос: " + String.join(", ", offers).toLowerCase());
                        
                        return meeting;
                    }
                }
            }
            
            return null; // Встреча не найдена
        } catch (Exception e) {
            System.out.println("Ошибка поиска встречи по ID: " + e.getMessage());
            return null;
        }
    }

    /** Еженедельная статистика по офферам (название -> суммарное количество за неделю) */
    public Map<String, Integer> getWeeklyStatsFromSheets(Long userId) {
        String sheetName = generateSheetName(userId);
        if (!safeSheetExists(sheetName)) return Collections.emptyMap();

        try {
            // читаем весь диапазон
            ValueRange vr = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, sheetName + "!A:ZZ").execute();

            List<List<Object>> rows = vr.getValues();
            if (rows == null || rows.isEmpty()) return Collections.emptyMap();

            // заголовки
            List<Object> header = rows.get(0);
            // Индексы колонок с офферами (по displayName)
            Map<String, Integer> offerIndex = new LinkedHashMap<>();
            List<String> offerNames = OfferType.getAllDisplayNames();
            for (int i = 0; i < header.size(); i++) {
                String h = String.valueOf(header.get(i)).trim();
                if (offerNames.contains(h)) {
                    offerIndex.put(h, i);
                }
            }
            if (offerIndex.isEmpty()) return Collections.emptyMap();

            Map<String, Integer> totals = new LinkedHashMap<>();
            offerIndex.keySet().forEach(k -> totals.put(k, 0));

            // суммируем числа по строкам, начиная со второй
            for (int r = 1; r < rows.size(); r++) {
                List<Object> row = rows.get(r);
                for (Map.Entry<String, Integer> e : offerIndex.entrySet()) {
                    int idx = e.getValue();
                    if (idx < row.size()) {
                        String val = String.valueOf(row.get(idx)).trim();
                        if (!val.isEmpty()) {
                            try {
                                totals.put(e.getKey(), totals.get(e.getKey()) + Integer.parseInt(val));
                            } catch (NumberFormatException ignore) {
                                // пропустим нечисловые
                            }
                        }
                    }
                }
            }
            // удалить нули для красоты
            totals.entrySet().removeIf(en -> en.getValue() == 0);
            return totals;
        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения статистики из Google Sheets", e);
        }
    }

    /** Запись переноса в отдельный недельный лист пользователя */
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

    /** Запись комментария в отдельный недельный лист пользователя */
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

    /** Еженедельная статистика переносов (причина -> количество) */
    public Map<String, Integer> getWeeklyRescheduleStatsFromSheets(Long userId) {
        String sheetName = generateRescheduleSheetName(userId);
        if (!safeSheetExists(sheetName)) return Collections.emptyMap();
        try {
            ValueRange vr = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, sheetName + "!A:D").execute();
            List<List<Object>> rows = vr.getValues();
            if (rows == null || rows.size() <= 1) return Collections.emptyMap();

            Map<String, Integer> map = new LinkedHashMap<>();
            for (int i = 1; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                if (row == null || row.isEmpty()) continue;
                String reason = getCell(row, 2); // колонка C: Причина
                if (reason.isEmpty()) continue;
                map.put(reason, map.getOrDefault(reason, 0) + 1);
            }
            return map;
        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения переносов из Google Sheets", e);
        }
    }

    /** Список комментариев за неделю (как список Meeting для совместимости) */
    public List<Meeting> getWeeklyCommentsFromSheets(Long userId) {
        String sheetName = generateCommentSheetName(userId);
        if (!safeSheetExists(sheetName)) return Collections.emptyList();
        try {
            ValueRange vr = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, sheetName + "!A:C").execute();
            List<List<Object>> rows = vr.getValues();
            if (rows == null || rows.size() <= 1) return Collections.emptyList();

            List<Meeting> out = new ArrayList<>();
            for (int i = 1; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                if (row == null || row.isEmpty()) continue;

                String dateStr = getCell(row, 0);
                String timeStr = getCell(row, 1);
                String comment = getCell(row, 2);

                LocalDate date = parseDate(dateStr);
                LocalTime time = parseTime(timeStr);
                LocalDateTime timestamp = (date == null ? LocalDate.now() : date).atTime(time == null ? LocalTime.MIDNIGHT : time);

                Meeting m = new Meeting(timestamp, Collections.emptyList(), "", userId);
                m.setComment(comment);
                m.setMeetingType(MeetingType.COMMENT);
                out.add(m);
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException("Ошибка чтения комментариев из Google Sheets", e);
        }
    }

    // ---------- Вспомогательные методы и создание листов ----------

    private String generateSheetName(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        // находим понедельник текущей недели (1..7)
        LocalDateTime monday = now.minusDays(now.getDayOfWeek().getValue() - 1L);
        LocalDateTime sunday = monday.plusDays(6);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d", java.util.Locale.forLanguageTag("ru"));
        String weekRange = monday.format(fmt) + "-" + sunday.format(fmt);
        return String.format("user%d_%s", userId, weekRange);
    }

    private String generateRescheduleSheetName(Long userId) {
        return generateSheetName(userId) + "_Переносы";
    }

    private String generateCommentSheetName(Long userId) {
        return generateSheetName(userId) + "_Комментарии";
    }

    private boolean sheetExists(String sheetName) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        return spreadsheet.getSheets().stream()
                .anyMatch(s -> sheetName.equals(s.getProperties().getTitle()));
    }

    private boolean safeSheetExists(String sheetName) {
        try {
            return sheetExists(sheetName);
        } catch (IOException e) {
            return false;
        }
    }

    private void createOfferSheet(String sheetName) throws IOException {
        addEmptySheet(sheetName);
        // заголовки: Дата, Время, ID активности, офферы...
        List<Object> headers = new ArrayList<>();
        headers.add("Дата");
        headers.add("Время");
        headers.add("ID активности");
        headers.addAll(OfferType.getAllDisplayNames());
        ValueRange vr = new ValueRange().setValues(Collections.singletonList(headers));
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A1", vr)
                .setValueInputOption("RAW").execute();
    }

    private void createRescheduleSheet(String sheetName) throws IOException {
        addEmptySheet(sheetName);
        List<Object> headers = Arrays.asList("Дата", "Время", "Причина", "Комментарий");
        ValueRange vr = new ValueRange().setValues(Collections.singletonList(headers));
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A1", vr)
                .setValueInputOption("RAW").execute();
    }

    private void createCommentSheet(String sheetName) throws IOException {
        addEmptySheet(sheetName);
        List<Object> headers = Arrays.asList("Дата", "Время", "Комментарий");
        ValueRange vr = new ValueRange().setValues(Collections.singletonList(headers));
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A1", vr)
                .setValueInputOption("RAW").execute();
    }

    private void addEmptySheet(String sheetName) throws IOException {
        AddSheetRequest add = new AddSheetRequest()
                .setProperties(new SheetProperties().setTitle(sheetName));
        BatchUpdateSpreadsheetRequest batch = new BatchUpdateSpreadsheetRequest()
                .setRequests(Collections.singletonList(new Request().setAddSheet(add)));
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batch).execute();
    }

    private void addMeetingRow(String sheetName, List<String> offers, String activityId) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        // подготавливаем строку: Дата, Время, ID активности, затем для каждого оффера "1" или ""
        List<Object> row = new ArrayList<>();
        row.add(now.format(DateTimeFormatter.ofPattern("dd.MM")));
        row.add(now.format(DateTimeFormatter.ofPattern("HH:mm")));
        row.add(activityId); // Добавляем ID активности

        // Получаем заголовки, чтобы понять порядок офферов
        ValueRange headerVr = sheetsService.spreadsheets().values()
                .get(spreadsheetId, sheetName + "!1:1").execute();
        List<Object> header = headerVr.getValues() != null && !headerVr.getValues().isEmpty() ? headerVr.getValues().get(0) : Collections.emptyList();

        Set<String> normalized = new HashSet<>();
        for (String o : offers) {
            if (o != null) normalized.add(o.trim());
        }

        for (int i = 3; i < header.size(); i++) { // начиная с 4-й колонки (после ID активности)
            String colName = String.valueOf(header.get(i)).trim();
            row.add(normalized.contains(colName) ? "1" : "");
        }

        appendRow(sheetName, row);
    }

    private void addRescheduleRow(String sheetName, String reason, String comment) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        List<Object> row = Arrays.asList(
                now.format(DateTimeFormatter.ofPattern("dd.MM")),
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                reason == null ? "" : reason,
                comment == null ? "" : comment
        );
        appendRow(sheetName, row);
    }

    private void addCommentRow(String sheetName, String comment) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        List<Object> row = Arrays.asList(
                now.format(DateTimeFormatter.ofPattern("dd.MM")),
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                comment == null ? "" : comment
        );
        appendRow(sheetName, row);
    }

    private void appendRow(String sheetName, List<Object> row) throws IOException {
        // находим следующую строку (колонка A)
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, sheetName + "!A:A").execute();
        int nextRow = (response.getValues() != null) ? response.getValues().size() + 1 : 2;

        ValueRange vr = new ValueRange().setValues(Collections.singletonList(row));
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A" + nextRow, vr)
                .setValueInputOption("RAW").execute();
    }

    private static String getCell(List<Object> row, int idx) {
        return idx < row.size() ? String.valueOf(row.get(idx)).trim() : "";
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        List<DateTimeFormatter> fmts = Arrays.asList(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd.MM"),
                DateTimeFormatter.ofPattern("dd/MM"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
        );
        for (DateTimeFormatter f : fmts) {
            try {
                LocalDate d = LocalDate.parse(s, f);
                // если год не указан, используем текущий
                if (f.equals(DateTimeFormatter.ofPattern("dd.MM")) || f.equals(DateTimeFormatter.ofPattern("dd/MM"))) {
                    return d.withYear(LocalDate.now().getYear());
                }
                return d;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static LocalTime parseTime(String s) {
        if (s == null || s.isEmpty()) return null;
        List<DateTimeFormatter> fmts = Arrays.asList(
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("H:mm")
        );
        for (DateTimeFormatter f : fmts) {
            try { return LocalTime.parse(s, f); } catch (Exception ignored) {}
        }
        return null;
    }
}
