package com.example.telegrambot;

import com.example.telegrambot.model.Meeting;
import com.example.telegrambot.service.GoogleSheetsService;
import com.example.telegrambot.service.MessageParserService;
import com.example.telegrambot.service.StatsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;

@Service
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${bot.username}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;

    private final MessageParserService messageParserService;
    private final StatsService statsService;
    private final GoogleSheetsService googleSheetsService;

    public TelegramBotService(MessageParserService messageParserService,
                              StatsService statsService,
                              GoogleSheetsService googleSheetsService) {
        this.messageParserService = messageParserService;
        this.statsService = statsService;
        this.googleSheetsService = googleSheetsService;
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
                handleStatsCommand(chatId);
            } else if (messageText.equals("/reset")) {
                Long userId = message.getFrom().getId();
                statsService.clearUserStats(userId);
                sendMessage(chatId, "✅ Вся ваша статистика очищена!");
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

    private void handleStatsCommand(long chatId) {
        try {
            Long userId = getUserIdFromChatId(chatId);

            // Получаем статистику из Google Sheets
            Map<String, Integer> weeklyStats = googleSheetsService.getWeeklyStatsFromSheets(userId);

            if (weeklyStats.isEmpty()) {
                // Если в Sheets пусто, пробуем локальную статистику
                weeklyStats = statsService.getWeeklyStats();
            }

            String statsText = statsService.formatStats(weeklyStats);
            sendMessage(chatId, statsText);
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при получении статистики: " + e.getMessage());
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

            // Сохраняем встречу локально (в JSON)
            statsService.saveMeeting(meeting);

            // Сохраняем в Google Sheets
            boolean savedToSheets = false;
            try {
                googleSheetsService.saveMeetingToSheets(userId, meeting.getOffers());
                savedToSheets = true;
                System.out.println("✅ Saved to Google Sheets for user: " + userId);
            } catch (Exception e) {
                System.err.println("❌ Error saving to Google Sheets: " + e.getMessage());
                e.printStackTrace();
            }

            // Отправляем подтверждение
            StringBuilder response = new StringBuilder("✅ Встреча сохранена");
            if (savedToSheets) {
                response.append(" в Google Sheets");
            }
            response.append("!\n\nНайденные офферы:\n");

            for (String offer : meeting.getOffers()) {
                response.append("• ").append(offer).append("\n");
            }

            sendMessage(chatId, response.toString());

        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при обработке встречи: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Long getUserIdFromChatId(long chatId) {
        return chatId; // Используем chatId как userId
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