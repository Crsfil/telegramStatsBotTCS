package com.example.telegrambot.service;

import com.example.telegrambot.model.Meeting;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
            return new Meeting(LocalDateTime.now(), new ArrayList<String>(), messageText, userId);
        }

        // Разбиваем на строки, затем каждую строку на слова
        List<String> offers = new ArrayList<>();

        String[] lines = offersText.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                // Разбиваем строку на слова по пробелам
                String[] words = trimmedLine.split("\\s+");
                for (String word : words) {
                    String trimmedWord = word.trim();
                    if (!trimmedWord.isEmpty()) {
                        offers.add(normalizeOffer(trimmedWord));
                    }
                }
            }
        }

        return new Meeting(LocalDateTime.now(), offers, messageText, userId);
    }

    private String normalizeOffer(String offer) {
        // Приводим к верхнему регистру и убираем лишние пробелы
        String normalized = offer.trim().toLowerCase();

        // Алиасы для офферов
        return switch (normalized) {
            case "кредитка", "кредитная карта", "кк" -> "КК";
            case "накопительный", "накопительный счет", "нс", "счет" -> "НС";
            case "брокерский", "брокерский счет", "инвест", "реактивация" -> "ИНВЕСТИЦИИ";
            case "авто", "автоследование" -> "ИНВЕСТ - АВТОСЛЕДОВАНИЕ";
            case "од", "обновление данных", "госуслуги", "обновление" -> "ОБНОВЛЕНИЕ ДАННЫХ";
            case "зк", "защита карты" -> "ЗАЩИТА КАРТЫ";
            case "мп" -> "МП";
            case "sim", "сим" -> "СИМ";
            case "сим мнп", "мнп" -> "SIM MNP";
            case "кредит" -> "КРЕДИТ НАЛИЧНЫМИ";
            case "джун" -> "ДЖУНИОР";
            case "дк", "дебет", "дебетовка" -> "ДК";
            case "прем", "premium", "премиум" -> "PREMIUM";
            case "приват", "прайват" -> "PRIVATE";
            case "про", "pro", "подписка" -> "PRO";
            case "соц счет" -> "СОЦИАЛЬНЫЙ СЧЕТ";
            case "оптимум" -> "КК+ОПТИМУМ";
            case "пд" -> "ПРИВЕДИ ДРУГА";
            case "утиль нс" -> "УТИЛИЗАЦИЯ НС";
            case "реф", "рефинанс", "рефинансирование" -> "РЕФИНАНСИРОВАНИЕ";
            case "инвест утиль" -> "ИНВЕСТИЦИИ УТИЛИЗАЦИЯ БС";
            case "мп инвест" -> "МП ИНВЕСТИЦИИ";
            case "бизнес", "рко", "ип"  -> "СЧЕТ ДЛЯ БИЗНЕСА";
            case "бк", "бизнес карта" -> "БИЗНЕС КАРТА";

            default -> normalized;
        };
    }
}