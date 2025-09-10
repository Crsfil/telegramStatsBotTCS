package com.example.telegrambot;

import com.example.telegrambot.model.Meeting;
import com.example.telegrambot.model.MeetingType;
import com.example.telegrambot.service.MessageParserService;
import com.example.telegrambot.service.StatsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${bot.username}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;

    private final MessageParserService messageParserService;
    private final StatsService statsService;
    
    // Хранилище состояний пользователей (кто ожидает модификацию текста)
    private final Map<Long, Boolean> userModifyMode = new HashMap<>();

    // Конструктор для dependency injection
    public TelegramBotService(MessageParserService messageParserService, StatsService statsService) {
        this.messageParserService = messageParserService;
        this.statsService = statsService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            String messageText = message.getText();
            long chatId = message.getChatId();

            // Обработка команд
            if (messageText.equals("/start")) {
                sendMessage(chatId, "Привет! 📊\n\nОтправь мне шаблон встречи и я буду считать статистику." +
                        "\n\nДля офферов:\nМой вопрос: кк нс инвест\n\n" +
                        "Для переносов:\nМой вопрос: перенос недозвон клиент не ответил\n\n" +
                        "Команды:\n/offers - статистика продаж\n/rescheduling - статистика переносов\n/" +
                        "meetings - встречи с комментариями\n/modify - получить модифицированный текст встречи\n/reset - очистить данные");
            } else if (messageText.equals("/offers")) {
                Long userId = message.getFrom().getId();
                Map<String, Integer> offerStats = statsService.getWeeklyOfferStats(userId);
                String statsText = statsService.formatOfferStats(offerStats);
                sendMessage(chatId, statsText);
            } else if (messageText.equals("/rescheduling")) {
                Long userId = message.getFrom().getId();
                Map<String, Integer> rescheduleStats = statsService.getWeeklyRescheduleStats(userId);
                String statsText = statsService.formatRescheduleStats(rescheduleStats);
                sendMessage(chatId, statsText);
            } else if (messageText.equals("/meetings")) {
                Long userId = message.getFrom().getId();
                List<Meeting> meetings = statsService.getWeeklyMeetingsWithComments(userId);
                String meetingsText = statsService.formatMeetingsWithComments(meetings);
                sendMessage(chatId, meetingsText);
            } else if (messageText.equals("/reset")) {
                Long userId = message.getFrom().getId();
                statsService.clearUserStats(userId);
                sendMessage(chatId, "✅ Вся ваша статистика очищена!");
            } else if (messageText.equals("/modify")) {
                Long userId = message.getFrom().getId();
                userModifyMode.put(userId, true);
                sendMessage(chatId, "🔍 Поиск встреч по ID активности:\n\n" +
                        "Отправьте ID активности для получения модифицированного текста встречи\n\n" +
                        "Пример: aOBv7DDXE4AqEPC8jHAqcA\n\n" +
                        "❌ Для выхода отправьте /cancel");
            } else if (messageText.equals("/cancel")) {
                Long userId = message.getFrom().getId();
                userModifyMode.remove(userId);
                sendMessage(chatId, "✅ Режим модификации отменен. Используйте /start для справки.");
            }
            // Обработка текста с офферами или переносами
            else if (messageText.toLowerCase().contains("мой вопрос:")) {
                Long userId = message.getFrom().getId();
                
                // Проверяем, находится ли пользователь в режиме модификации
                if (userModifyMode.getOrDefault(userId, false)) {
                    handleModifyText(chatId, messageText, userId);
                    userModifyMode.remove(userId); // Выходим из режима модификации
                } else {
                    handleMeetingMessage(chatId, messageText, message);
                }
            } else {
                Long userId = message.getFrom().getId();
                if (userModifyMode.getOrDefault(userId, false)) {
                    handleModifyModeInput(chatId, messageText, userId);
                } else {
                    sendMessage(chatId, "Не понял команду. Используй /start для справки.");
                }
            }
        }
    }

    private void handleMeetingMessage(long chatId, String messageText, Message message) {
        try {
            Long userId = message.getFrom().getId();
            Meeting meeting = messageParserService.parseMeetingMessage(messageText, userId);

            if (meeting == null) {
                sendMessage(chatId, "❌ Не могу найти 'Мой вопрос:' в сообщении");
                return;
            }
            // Сохраняем встречу (любого типа)
            statsService.saveMeeting(meeting);
            // Формируем ответ в зависимости от типа встречи
            StringBuilder response = new StringBuilder();

            if (meeting.getMeetingType() == MeetingType.RESCHEDULED) {
                response.append("📅 Перенос зафиксирован!\n\n");
                response.append("Причина: ").append(meeting.getRescheduleReason()).append("\n");
                response.append("Комментарий: ").append(meeting.getComment());
            } else if (meeting.getMeetingType() == MeetingType.COMMENT) {
                response.append("💬 Комментарий сохранен!\n\n");
                response.append("Текст: ").append(meeting.getComment());
            } else {
                if (meeting.getOffers().isEmpty()) {
                    sendMessage(chatId, "❌ Не найдено офферов после 'Мой вопрос:'");
                    return;
                }

                response.append("✅ Встреча сохранена!\n\nНайденные офферы:\n");
                for (String offer : meeting.getOffers()) {
                    response.append("• ").append(offer).append("\n");
                }
            }

            sendMessage(chatId, response.toString());

        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при обработке встречи: " + e.getMessage());
        }
    }

    private void handleModifyText(long chatId, String messageText, Long userId) {
        try {
            String modifiedText = statsService.getModifiedMeetingText(userId, messageText);
            sendMessage(chatId, "📝 Модифицированный текст встречи:\n\n" + modifiedText);
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при получении модифицированного текста: " + e.getMessage());
        }
    }

    private void handleModifyModeInput(long chatId, String input, Long userId) {
        try {
            // Обрезаем пробелы и ищем встречу по ID
            String trimmedInput = input.trim();
            
            if (isActivityId(trimmedInput)) {
                String modifiedText = statsService.getModifiedMeetingTextById(trimmedInput);
                if (modifiedText != null && !modifiedText.isEmpty()) {
                    sendMessage(chatId, "📝 Модифицированный текст встречи:\n\n" + modifiedText);
                } else {
                    sendMessage(chatId, "❌ Встреча с ID " + trimmedInput + " не найдена");
                }
                userModifyMode.remove(userId); // Выходим из режима модификации
                return;
            }
            
            // Если не распознали формат
            sendMessage(chatId, "❌ Неверный формат ввода.\n\n" +
                    "🔍 Для поиска по ID отправьте ID активности (например: aOBv7DDXE4AqEPC8jHAqcA)\n\n" +
                    "❌ Для выхода отправьте /cancel");
            
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при обработке ввода: " + e.getMessage());
        }
    }

    private boolean isActivityId(String input) {
        // ID активности обычно содержит буквы и цифры, длина больше 10 символов
        return input != null && input.length() > 10 && input.matches("[a-zA-Z0-9]+");
    }

    private boolean isDateInput(String input) {
        // Проверяем формат ДД.ММ
        return input != null && input.matches("\\d{1,2}\\.\\d{1,2}");
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}