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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
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
                    sendAndTrack(chatId, telegramId, response);
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

                SendMessage response;

                if (text.startsWith("/")) {
                    response = switch (text) {
                        case "/start" -> {
                            userService.clearWaitingForTaskNumber(telegramId);
                            userService.clearWaitingForFile(telegramId);
                            yield userCommandHandler.handleStartCommand(chatId, telegramId, username);
                        }
                        case "/help" -> userCommandHandler.handleHelpCommand(chatId);
                        case "/tasks" -> userCommandHandler.handleMyTasksCommand(chatId, telegramId);
                        case "/pending" -> {
                            if (userService.isBoss(telegramId)) {
                                yield userCommandHandler.handlePendingRequests(chatId, telegramId);
                            } else {
                                yield new SendMessage(chatId.toString(), "⚠️ Доступ запрещен. Эта команда доступна только руководителям.");
                            }
                        }
                        default -> new SendMessage(chatId.toString(),
                                "❓ Команда не распознана. Используйте /help для списка доступных команд.");
                    };
                    sendAndTrack(chatId, telegramId, response); // Handle deletion and tracking outside the switch
                    return;
                }

                // Registration process
                if (!userService.existsByTelegramId(telegramId)) {
                    response = userCommandHandler.handleRegistrationStep(chatId, telegramId, text);
                    sendAndTrack(chatId, telegramId, response);
                    return;
                }

                // Extension reason input
                if (userService.isWaitingForExtensionReason(telegramId)) {
                    response = userCommandHandler.handleExtensionReasonInput(chatId, telegramId, text);
                    sendAndTrack(chatId, telegramId, response);
                    return;
                }

                // Waiting for task number
                if (userService.isWaitingForTaskNumber(telegramId)) {
                    response = userCommandHandler.handleTaskSelection(chatId, telegramId, text);
                    execute(response);
                    return;
                }

                // Text result for document
                if (userService.isWaitingForFile(telegramId)) {
                    response = userCommandHandler.handleTextResultSubmission(chatId, telegramId, text);
                    execute(response);
                    return;
                }

                // Default case
                response = new SendMessage(chatId.toString(),
                        "❓ Команда не распознана. Используйте /help для списка доступных команд.");
                execute(response);
            }

        } catch (TelegramApiException e) {
            log.error("❌ Telegram API error: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error in UserBot: {}", e.getMessage(), e);
        }
    }

    private void deletePreviousMessage(Long chatId, Long telegramId) {
        Integer lastMsgId = userService.getLastMessageId(telegramId);
        if (lastMsgId != null) {
            try {
                DeleteMessage deleteMessage = new DeleteMessage(chatId.toString(), lastMsgId);
                execute(deleteMessage);
                log.info("🗑 Successfully deleted previous message {} for user {}", lastMsgId, telegramId);
                userService.setLastMessageId(telegramId, null);
            } catch (TelegramApiException e) {
                log.info("⚠ Failed to delete message {} for user {}: {}", lastMsgId, telegramId, e.getMessage(), e);
            }
        } else {
            log.info("ℹ No previous message to delete for telegramId {}", telegramId);
        }
    }

    private void sendAndTrack(Long chatId, Long telegramId, SendMessage response) throws TelegramApiException {
        if (response == null) {
            log.info("⚠ No response to send for telegramId {}", telegramId);
            return;
        }
        deletePreviousMessage(chatId, telegramId);
        Message sent = execute(response);
        if (sent != null && sent.getMessageId() != null) {
            userService.setLastMessageId(telegramId, sent.getMessageId());
            log.info("💬 Successfully sent message {} to telegramId {}", sent.getMessageId(), telegramId);
        } else {
            log.info("⚠ Failed to retrieve messageId after sending to telegramId {}", telegramId);
        }
    }
}