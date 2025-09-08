package com.example.telegrambot;

import com.example.telegrambot.model.Meeting;
import com.example.telegrambot.service.MessageParserService;
import com.example.telegrambot.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;

@Component
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${bot.username}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;

    private final MessageParserService messageParserService;
    private final StatsService statsService;

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
                sendMessage(chatId, "Привет! 📊\n\nОтправь мне текст встречи с 'Мой вопрос:' и я буду считать статистику по офферам.\n\nКоманды:\n/stats - показать статистику за неделю");
            }
            else if (messageText.equals("/stats")) {
                Map<String, Integer> weeklyStats = statsService.getWeeklyStats();
                String statsText = statsService.formatStats(weeklyStats);
                sendMessage(chatId, statsText);
            }
            // Обработка текста с офферами
            else if (messageText.toLowerCase().contains("мой вопрос:")) {
                handleMeetingMessage(chatId, messageText, message);
            }
            else {
                sendMessage(chatId, "Не понял команду. Используй /start для справки.");
            }
        }
    }

    private void handleMeetingMessage(long chatId, String messageText, Message message) {
        try {
            Long userId = getUserIdFromChatId(chatId);
            Meeting meeting = messageParserService.parseMeetingMessage(messageText, userId);

            if (meeting == null) {
                sendMessage(chatId, "❌ Не могу найти 'Мой вопрос:' в сообщении");
                return;
            }

            if (meeting.getOffers().isEmpty()) {
                sendMessage(chatId, "❌ Не найдено офферов после 'Мой вопрос:'");
                return;
            }

            // Сохраняем встречу
            statsService.saveMeeting(meeting);

            // Отправляем подтверждение
            StringBuilder response = new StringBuilder("✅ Встреча сохранена!\n\nНайденные офферы:\n");
            for (String offer : meeting.getOffers()) {
                response.append("• ").append(offer).append("\n");
            }

            sendMessage(chatId, response.toString());

        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при обработке встречи: " + e.getMessage());
        }
    }

    // Временный метод - нужно получать userId из Update
    private Long getUserIdFromChatId(long chatId) {
        return chatId; // Пока используем chatId как userId
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