package com.example.telegrambot.model;

import java.util.Arrays;
import java.util.List;

public enum RescheduleReason {
    НЕДОЗВОН("недозвон", Arrays.asList(
            "недозвон", "не взял трубку"
    )),

    НЕ_ЯВИЛСЯ_НА_АДРЕС("не явился на адрес", Arrays.asList(
            "по другому адресу", "другой адрес"
    )),

    В_ДРУГОМ_ГОРОДЕ("не в городе", Arrays.asList(
            "за городом", "не в городе"
    )),

    ИНИЦИАТИВА_КЛИЕНТА("Инициатива клиента", Arrays.asList(
            "просит перенести", "попросил", "хочет перенести", "не может сегодня", "занят", "срочные дела"
    )),

    ВИНА_ПРЕДСТАВИТЕЛЯ("вина представителя", Arrays.asList(
            "опоздал", "не успел", "много встреч", "большая нагрузка"
    )),
    НЕТ_ПАСПОРТА("нет документов", Arrays.asList(
            "без паспорта", "нет паспорта", "забыл паспорт")),

    ОТКАЗ("отказ", Arrays.asList("отказ")),

    ДРУГОЕ("другое коммент", Arrays.asList());

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
        String lowerText = text.toLowerCase();

        return Arrays.stream(values())
                .filter(reason -> reason != ДРУГОЕ) // ДРУГОЕ проверяем в конце
                .filter(reason -> reason.getKeywords().stream()
                        .anyMatch(lowerText::contains))
                .findFirst()
                .orElse(ДРУГОЕ);
    }

    // Получить все названия причин для статистики
    public static List<String> getAllDisplayNames() {
        return Arrays.stream(values())
                .map(RescheduleReason::getDisplayName)
                .toList();
    }
}