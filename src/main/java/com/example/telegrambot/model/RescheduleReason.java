package com.example.telegrambot.model;

import java.util.Arrays;
import java.util.List;

public enum RescheduleReason {
    ОТКАЗ("отказ", Arrays.asList("отказ")),
    НЕДОЗВОН("недозвон", Arrays.asList(
            "недозвон"
    )),

    НЕ_ЯВИЛСЯ_НА_АДРЕС("не явился по адресу", List.of("не явился по адресу")),

    В_ДРУГОМ_ГОРОДЕ("не в городе", Arrays.asList(
            "за городом", "не в городе"
    )),

    ИНИЦИАТИВА_КЛИЕНТА("Инициатива клиента", Arrays.asList(
            "просит перенести", "попросил перенести", "попросил перенести", "попросил перенести встречу", "инициатива клиента"
    )),

    ВИНА_ПРЕДСТАВИТЕЛЯ("вина представителя", Arrays.asList(
            "не успел доехать", "не успел на встречу", "не успел", "вина представителя"
    )),

    НЕТ_ПАСПОРТА("нет паспорта", Arrays.asList(
            "нет паспорта", "забыл паспорт")),

    НЕТ_ДОКУМЕНТОВ("нет документов", Arrays.asList("нет конверта", "не пришли доки", "нет доков")),

    ДРУГОЕ("другое", List.of());

    private final String displayName;
    private final List<String> keywords;

    RescheduleReason(String displayName, List<String> keywords) {
        this.displayName = displayName;
        this.keywords = keywords;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    // Найти причину переноса по ключевым словам в тексте
    public static RescheduleReason findByKeywords(String text) {
        String lowerText = text.toLowerCase().trim();

        return Arrays.stream(values())
                .filter(reason -> reason != ДРУГОЕ) // ДРУГОЕ проверяем в конце
                .filter(reason -> reason.getKeywords().stream()
                        .anyMatch(keyword -> containsKeyPhrase(lowerText, keyword)))
                .findFirst()
                .orElse(ДРУГОЕ);
    }

    // Проверяет содержится ли ключевая фраза целиком в тексте
    private static boolean containsKeyPhrase(String text, String keyPhrase) {
        String normalizedText = normalizeText(text);
        String normalizedPhrase = normalizeText(keyPhrase);

        return normalizedText.contains(normalizedPhrase);
    }

    // Нормализует текст - убирает лишние пробелы и знаки препинания
    private static String normalizeText(String text) {
        return text.toLowerCase()
                .replaceAll("[^а-яёa-z\\s]", " ") // заменяем знаки препинания на пробелы
                .replaceAll("\\s+", " ") // убираем множественные пробелы
                .trim();
    }


    // Получить все названия причин для статистики
    public static List<String> getAllDisplayNames() {
        return Arrays.stream(values())
                .map(RescheduleReason::getDisplayName)
                .toList();
    }
}