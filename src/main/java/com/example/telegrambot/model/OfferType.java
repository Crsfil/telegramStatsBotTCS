package com.example.telegrambot.model;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum OfferType {
    КК("КК", Arrays.asList("кредитка", "кредитная карта", "кк")),
    НС("НС", Arrays.asList("накопительный", "накопительный счет", "нс", "счет")),
    ИНВЕСТИЦИИ("ИНВЕСТИЦИИ", Arrays.asList("брокерский", "брокерский счет", "инвест", "реактивация")),
    ИНВЕСТ_АВТОСЛЕДОВАНИЕ("ИНВЕСТ - АВТОСЛЕДОВАНИЕ", Arrays.asList("авто", "автоследование")),
    ОБНОВЛЕНИЕ_ДАННЫХ("ОБНОВЛЕНИЕ ДАННЫХ", Arrays.asList("од", "обновление данных", "госуслуги", "обновление")),
    ЗАЩИТА_КАРТЫ("ЗАЩИТА КАРТЫ", Arrays.asList("зк", "защита карты")),
    МП("МП", Arrays.asList("мп")),
    СИМ("СИМ", Arrays.asList("sim", "сим")),
    SIM_MNP("СИМ+MNP", Arrays.asList("сим мнп", "мнп")),
    КРЕДИТ_НАЛИЧНЫМИ("КРЕДИТ НАЛИЧНЫМИ", Arrays.asList("кредит", "кн")),
    ДЖУНИОР("ДЖУНИОР", Arrays.asList("джун")),
    ДК("ДК", Arrays.asList("дк", "дебет", "дебетовка")),
    PREMIUM("PREMIUM", Arrays.asList("прем", "premium", "премиум")),
    PRIVATE("PRIVATE", Arrays.asList("приват", "прайват")),
    PRO("PRO", Arrays.asList("про", "pro", "подписка")),
    СОЦИАЛЬНЫЙ_СЧЕТ("СОЦИАЛЬНЫЙ СЧЕТ", Arrays.asList("соц счет", "cc")),
    КК_ОПТИМУМ("ОПТИМУМ", Arrays.asList("оптимум")),
    ПРИВЕДИ_ДРУГА("ПРИВЕДИ ДРУГА", Arrays.asList("пд")),
    УТИЛИЗАЦИЯ_НС("УТИЛИЗАЦИЯ НС", Arrays.asList("утиль нс", "унс")),
    РЕФИНАНСИРОВАНИЕ("РЕФИНАНСИРОВАНИЕ", Arrays.asList("реф", "рефинанс", "рефинансирование")),
    ИНВЕСТИЦИИ_УТИЛИЗАЦИЯ_БС("ИНВЕСТИЦИИ УТИЛИЗАЦИЯ БС", Arrays.asList("инвест утиль", "утиль инвест")),
    МП_ИНВЕСТИЦИИ("МП ИНВЕСТИЦИИ", Arrays.asList("мп инвест")),
    СЧЕТ_ДЛЯ_БИЗНЕСА("СЧЕТ ДЛЯ БИЗНЕСА", Arrays.asList("бизнес", "рко", "ип")),
    БИЗНЕС_КАРТА("БИЗНЕС КАРТА", Arrays.asList("бк", "бизнес карта"));

    private final String displayName;
    private final List<String> aliases;

    OfferType(String displayName, List<String> aliases) {
        this.displayName = displayName;
        this.aliases = aliases;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getAliases() {
        return aliases;
    }

    // Найти оффер по алиасу
    public static OfferType findByAlias(String alias) {
        String searchKey = alias.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(offer -> offer.getAliases().contains(searchKey))
                .findFirst()
                .orElse(null);
    }

    // Получить все названия офферов для Google Sheets
    public static List<String> getAllDisplayNames() {
        return Arrays.stream(values())
                .map(OfferType::getDisplayName)
                .collect(Collectors.toList());
    }
}