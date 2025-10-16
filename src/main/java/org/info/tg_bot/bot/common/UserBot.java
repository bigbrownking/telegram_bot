package org.info.tg_bot.bot.common;

import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.handlers.UserCommandHandler;
import org.info.tg_bot.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
public class UserBot extends TelegramLongPollingBot {

    private final UserCommandHandler userCommandHandler;
    private final UserService userService;

    @Value("${tg.user.token}")
    private String botToken;

    @Value("${tg.user.username}")
    private String botUsername;

    public UserBot(
            UserCommandHandler userCommandHandler,
            UserService userService,
            DefaultBotOptions options,
            @Value("${tg.user.token}") String botToken,
            @Value("${tg.user.username}") String botUsername
    ) {
        super(options, botToken);
        this.userCommandHandler = userCommandHandler;
        this.userService = userService;
        this.botToken = botToken;
        this.botUsername = botUsername;
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
        try {
            // 1️⃣ Handle callback buttons
            if (update.hasCallbackQuery()) {
                CallbackQuery callback = update.getCallbackQuery();
                String callbackData = callback.getData();
                String callbackQueryId = callback.getId();
                Long chatId = callback.getMessage().getChatId();
                Long telegramId = callback.getFrom().getId();

                if (callbackData.startsWith("approve_extend:")) {
                    Long requestId = Long.parseLong(callbackData.split(":")[1]);
                    execute(userCommandHandler.handleApproveExtendRequest(chatId, requestId, telegramId));
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }

                if (callbackData.startsWith("reject_extend:")) {
                    Long requestId = Long.parseLong(callbackData.split(":")[1]);
                    execute(userCommandHandler.handleRejectExtendRequest(chatId, requestId, telegramId));
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }

                if (!userService.existsByTelegramId(telegramId)) {
                    SendMessage response = userCommandHandler.handleRegistrationStep(chatId, telegramId, callbackData);
                    execute(response);
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }

                SendMessage response = userCommandHandler.handleCallback(chatId, telegramId, callbackData);
                execute(response);
                execute(new AnswerCallbackQuery(callbackQueryId));
                return;
            }

            // 2️⃣ Handle file messages
            if (update.hasMessage() && update.getMessage().hasDocument()) {
                Long chatId = update.getMessage().getChatId();
                Long telegramId = update.getMessage().getFrom().getId();

                if (userService.isWaitingForFile(telegramId)) {
                    org.telegram.telegrambots.meta.api.objects.Document tgDocument = update.getMessage().getDocument();
                    SendMessage response = userCommandHandler.handleFileUpload(chatId, telegramId, tgDocument);
                    execute(response);
                } else {
                    execute(new SendMessage(chatId.toString(), "⚠️ Сейчас не ожидается загрузка файла."));
                }
                return;
            }

            // 3️⃣ Handle text messages
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText().trim();
                Long chatId = update.getMessage().getChatId();
                Long telegramId = update.getMessage().getFrom().getId();
                String username = update.getMessage().getFrom().getUserName();

                if (text.startsWith("/")) {
                    SendMessage response = switch (text) {
                        case "/start" -> {
                            userService.clearWaitingForTaskNumber(telegramId);
                            userService.clearWaitingForFile(telegramId);
                            yield userCommandHandler.handleStartCommand(chatId, telegramId, username);
                        }
                        case "/help" -> userCommandHandler.handleHelpCommand(chatId);
                        case "/tasks" -> userCommandHandler.handleMyTasksCommand(chatId, telegramId);
                        case "/pending" -> {
                            if (userService.isBoss(telegramId)) {
                                yield userCommandHandler.handlePendingRequests(chatId);
                            } else {
                                yield new SendMessage(chatId.toString(), "⚠️ Доступ запрещен. Эта команда доступна только руководителям.");
                            }
                        }
                        default -> new SendMessage(chatId.toString(),
                                "❓ Команда не распознана. Используйте /help для списка доступных команд.");
                    };
                    execute(response);
                    return;
                }

                // Registration process
                if (!userService.existsByTelegramId(telegramId)) {
                    SendMessage response = userCommandHandler.handleRegistrationStep(chatId, telegramId, text);
                    execute(response);
                    return;
                }

                // Extension reason input
                if (userService.isWaitingForExtensionReason(telegramId)) {
                    SendMessage response = userCommandHandler.handleExtensionReasonInput(chatId, telegramId, text);
                    execute(response);
                    return;
                }

                // Обработка продления срока
                if (userService.isWaitingForNewDate(telegramId)) {
                    execute(userCommandHandler.handleNewExecutionDate(chatId, telegramId, text));
                    return;
                }

                // Waiting for task number
                if (userService.isWaitingForTaskNumber(telegramId)) {
                    SendMessage response = userCommandHandler.handleTaskSelection(chatId, telegramId, text);
                    execute(response);
                    return;
                }

                // Text result for document
                if (userService.isWaitingForFile(telegramId)) {
                    SendMessage response = userCommandHandler.handleTextResultSubmission(chatId, telegramId, text);
                    execute(response);
                    return;
                }

                // Default case
                SendMessage response = new SendMessage(chatId.toString(),
                        "❓ Команда не распознана. Используйте /help для списка доступных команд.");
                execute(response);
            }

        } catch (TelegramApiException e) {
            log.error("❌ Telegram API error: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error in UserBot: {}", e.getMessage(), e);
        }
    }
}
