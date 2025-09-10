package com.example.telegrambot.service;

import com.example.telegrambot.model.Meeting;
import com.example.telegrambot.model.OfferType;
import com.example.telegrambot.model.RescheduleReason;
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
        String contentText = messageText.substring(questionIndex + "мой вопрос:".length()).trim();

        if (contentText.isEmpty()) {
            return new Meeting(LocalDateTime.now(), new ArrayList<>(), messageText, userId);
        }

        // Проверяем начинается ли с "перенос"
        if (contentText.toLowerCase().startsWith("перенос")) {
            return parseRescheduleMessage(contentText, messageText, userId);
        } else if (contentText.toLowerCase().startsWith("комментарий")) {
            return parseCommentMessage(contentText, messageText, userId);
        } else {
            return parseOffersMessage(contentText, messageText, userId);
        }
    }

    private Meeting parseCommentMessage(String commentText, String originalText, Long userId) {
        String comment = commentText.substring(11).trim(); // "комментарий".length() = 11
        return new Meeting(LocalDateTime.now(), originalText, userId, comment);
    }

    private Meeting parseRescheduleMessage(String rescheduleText, String originalText, Long userId) {
        // Убираем слово "перенос" и получаем текст с причиной и комментарием
        String reasonAndComment = rescheduleText.substring(7).trim(); // "перенос".length() = 7

        // Автоматически определяем причину по ключевым словам
        RescheduleReason reason = RescheduleReason.findByKeywords(reasonAndComment);

        // Возвращаем встречу типа "перенос" - используем правильный конструктор
        return new Meeting(LocalDateTime.now(), originalText, userId,
                reason.getDisplayName(), reasonAndComment);
    }

    private Meeting parseOffersMessage(String offersText, String originalText, Long userId) {
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

        return new Meeting(LocalDateTime.now(), offers, originalText, userId);
    }

    private String normalizeOffer(String offer) {
        OfferType offerType = OfferType.findByAlias(offer);
        return offerType != null ? offerType.getDisplayName() : offer.trim().toUpperCase();
    }
}