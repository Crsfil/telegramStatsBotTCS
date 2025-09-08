package com.example.telegrambot.service;

import com.example.telegrambot.model.Meeting;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageParserService {

    public Meeting parseMeetingMessage(String messageText, Long userId) {
        // Ищем "Мой вопрос:" (игнорируя регистр)
        String lowerText = messageText.toLowerCase();
        int questionIndex = lowerText.indexOf("мой вопрос:");

        if (questionIndex == -1) {
            return null; // Не найдено "Мой вопрос:"
        }

        // Извлекаем текст после "Мой вопрос:"
        String offersText = messageText.substring(questionIndex + "мой вопрос:".length()).trim();

        if (offersText.isEmpty()) {
            return new Meeting(LocalDateTime.now(), List.of(), messageText, userId);
        }

        // Разбиваем на строки и очищаем
        List<String> offers = Arrays.stream(offersText.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(this::normalizeOffer)
                .collect(Collectors.toList());

        return new Meeting(LocalDateTime.now(), offers, messageText, userId);
    }

    private String normalizeOffer(String offer) {
        // Приводим к верхнему регистру и убираем лишние пробелы
        String normalized = offer.trim().toUpperCase();

        // Алиасы для офферов
        return switch (normalized) {
            case "КРЕДИТКА", "КРЕДИТНАЯ КАРТА" -> "КК";
            case "НАКОПИТЕЛЬНЫЙ", "НАКОПИТЕЛЬНЫЙ СЧЕТ", "НС", "СЧЕТ" -> "НС";
            case "БРОКЕРСКИЙ", "БРОКЕРСКИЙ СЧЕТ", "ИНВЕСТ", "РЕАКТИВАЦИЯ" -> "ИНВЕСТИЦИИ";
            case "АВТО", "АВТОСЛЕДОВАНИЕ" -> "ИНВЕСТ - АВТОСЛЕДОВАНИЕ";
            case "ОД", "ОБНОВЛЕНИЕ ДАННЫХ", "ГОСУСЛУГИ", "ОБНОВЛЕНИЕ" -> "ОБНОВЛЕНИЕ ДАННЫХ";
            case "ЗК", "ЗАЩИТА КАРТЫ" -> "ЗАЩИТА КАРТЫ";
            case "МП" -> "МП";
            case "SIM", "СИМ" -> "СИМ";
            case "КРЕДИТ" -> "КРЕДИТ НАЛИЧНЫМИ";
            case "ДЖУН" -> "ДЖУНИОР";
            case "ДК", "ДЕБЕТ", "ДЕБЕТОВКА" -> "ДК";
            case "ПРЕМ", "PREMIUM", "ПРЕМИУМ" -> "PREMIUM";
            case "ПРИВАТ", "ПРАЙВАТ" -> "PRIVATE";
            case "ПРО", "ПОДПИСКА" -> "PRO";
            case "СОЦ СЧЕТ" -> "СОЦИАЛЬНЫЙ СЧЕТ";
            case "ОПТИМУМ" -> "КК+ОПТИМУМ";
            default -> normalized;
        };
    }
}