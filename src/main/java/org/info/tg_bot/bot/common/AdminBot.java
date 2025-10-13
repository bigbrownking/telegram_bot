package org.info.tg_bot.bot.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.handlers.AdminCommandHandler;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.service.DocumentService;
import org.info.tg_bot.service.NotificationService;
import org.info.tg_bot.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBot extends TelegramLongPollingBot {

    private final AdminCommandHandler adminCommandHandler;
    private final UserService userService;

    @Value("${tg.admin.token}")
    private String botToken;

    @Value("${tg.admin.username}")
    private String botUsername;

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
        try {
            Long telegramId = null;
            Long chatId = null;

            if (update.hasMessage()) {
                telegramId = update.getMessage().getFrom().getId();
                chatId = update.getMessage().getChatId();
            } else if (update.hasCallbackQuery()) {
                telegramId = update.getCallbackQuery().getFrom().getId();
                chatId = update.getCallbackQuery().getMessage().getChatId();
            }

            if (telegramId == null || chatId == null) return;

            Optional<User> user = userService.findByTelegramId(telegramId);
            if (user.isEmpty() || !user.get().isAdmin()) {
                execute(new SendMessage(chatId.toString(), "🚫 У вас нет доступа к этому боту."));
                return;
            }

            // 🧩 1. Обработка callback-кнопок
            if (update.hasCallbackQuery()) {
                String callbackData = update.getCallbackQuery().getData();
                String callbackQueryId = update.getCallbackQuery().getId();

                if (callbackData.startsWith("approve_extend:")) {
                    Long requestId = Long.parseLong(callbackData.split(":")[1]);
                    SendMessage msg = adminCommandHandler.handleApproveExtendRequest(chatId, requestId, telegramId);
                    execute(msg);

                    // Отправляем подтверждение callback
                    AnswerCallbackQuery answer = new AnswerCallbackQuery(callbackQueryId);
                    execute(answer);
                    return;
                }

                if (callbackData.startsWith("reject_extend:")) {
                    Long requestId = Long.parseLong(callbackData.split(":")[1]);
                    SendMessage msg = adminCommandHandler.handleRejectExtendRequest(chatId, requestId, telegramId);
                    execute(msg);

                    // Отправляем подтверждение callback
                    AnswerCallbackQuery answer = new AnswerCallbackQuery(callbackQueryId);
                    execute(answer);
                    return;
                }
            }
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                String username = update.getMessage().getFrom().getUserName();

                if (adminCommandHandler.isWaitingForNewDate(telegramId)) {
                    SendMessage msg = adminCommandHandler.handleNewExecutionDate(chatId, telegramId, text);
                    execute(msg);
                    return;
                }

                // стандартные команды
                SendMessage response = switch (text) {
                    case "/start" -> adminCommandHandler.handleStartCommand(chatId, username);
                    case "/help" -> adminCommandHandler.handleHelpCommand(chatId);
                    case "/upload" -> adminCommandHandler.handleUploadCommand(chatId);
                    case "/pending" -> adminCommandHandler.handlePendingRequests(chatId);
                    default -> new SendMessage(chatId.toString(),
                            "❓ Команда не распознана. Используйте /help для списка доступных команд.");
                };

                execute(response);
                return;
            }

            // 📂 3. Обработка загрузки Excel-файлов
            if (update.hasMessage() && update.getMessage().hasDocument()) {
                String fileName = update.getMessage().getDocument().getFileName();
                String fileId = update.getMessage().getDocument().getFileId();

                SendMessage response = adminCommandHandler.handleExcelUpload(chatId, telegramId, fileName, fileId, this);
                execute(response);
            }

        } catch (TelegramApiException e) {
            log.error("Error sending message", e);
        } catch (Exception e) {
            log.error("Unexpected error", e);
        }
    }
}
