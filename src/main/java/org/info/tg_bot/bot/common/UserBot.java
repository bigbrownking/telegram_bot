package org.info.tg_bot.bot.common;

import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.handlers.ProfileCommandHandler;
import org.info.tg_bot.bot.handlers.UserCommandHandler;
import org.info.tg_bot.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
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
    private final ProfileCommandHandler profileCommandHandler;
    private final UserService userService;

    @Value("${tg.user.token}")
    private String botToken;

    @Value("${tg.user.username}")
    private String botUsername;

    public UserBot(
            UserCommandHandler userCommandHandler,
            ProfileCommandHandler profileCommandHandler,
            UserService userService,
            DefaultBotOptions options,
            @Value("${tg.user.token}") String botToken,
            @Value("${tg.user.username}") String botUsername
    ) {
        super(options, botToken);
        this.userCommandHandler = userCommandHandler;
        this.profileCommandHandler = profileCommandHandler;
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
            // 1. Handle callback buttons
            if (update.hasCallbackQuery()) {
                handleCallback(update);
                return;
            }

            // 2. Handle file messages
            if (update.hasMessage() && update.getMessage().hasDocument()) {
                handleFileMessage(update);
                return;
            }

            // 3. Handle text messages
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update);
                return;
            }

        } catch (TelegramApiException e) {
            log.error("Telegram API error: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error in UserBot: {}", e.getMessage(), e);
        }
    }

    // --- CALLBACK HANDLER ---
    private void handleCallback(Update update) throws TelegramApiException {
        CallbackQuery callback = update.getCallbackQuery();
        String callbackData = callback.getData();
        String callbackQueryId = callback.getId();
        Long chatId = callback.getMessage().getChatId();
        Long telegramId = callback.getFrom().getId();
        Integer callbackMessageId = callback.getMessage().getMessageId();

        // УДАЛЯЕМ СООБЩЕНИЕ С КНОПКАМИ
        deleteMessageSilently(chatId, callbackMessageId);

        deletePreviousMessage(chatId, telegramId);

        SendMessage response;

        // --- Регистрация ---
        if (!userService.existsByTelegramId(telegramId)) {
            response = userCommandHandler.handleRegistrationStep(chatId, telegramId, callbackData);
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        // --- Профиль ---
        if (callbackData.startsWith(ProfileCommandHandler.CB_PREFIX)) {
            response = profileCommandHandler.handleCallback(chatId, telegramId, callbackData);
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        // Любая другая кнопка прерывает ввод в профиле
        userService.clearProfileEditField(telegramId);

        // --- Одобрение/отклонение ---
        if (callbackData.startsWith("approve_extend:")) {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            response = userCommandHandler.handleApproveExtendRequest(chatId, requestId, telegramId);
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        if (callbackData.startsWith("reject_extend:")) {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            response = userCommandHandler.handleRejectExtendRequest(chatId, requestId, telegramId);
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        // --- Остальные действия ---
        response = userCommandHandler.handleCallback(chatId, telegramId, callbackData);
        sendAndTrack(chatId, telegramId, response);
        execute(new AnswerCallbackQuery(callbackQueryId));
    }

    // --- FILE HANDLER ---
    private void handleFileMessage(Update update) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();

        deletePreviousMessage(chatId, telegramId);

        if (userService.isWaitingForFile(telegramId)) {
            var tgDocument = update.getMessage().getDocument();
            SendMessage response = userCommandHandler.handleFileUpload(chatId, telegramId, tgDocument);
            sendAndTrack(chatId, telegramId, response);
        } else {
            sendAndTrack(chatId, telegramId, new SendMessage(chatId.toString(), "Сейчас не ожидается загрузка файла."));
        }
    }

    // --- TEXT HANDLER ---
    private void handleTextMessage(Update update) throws TelegramApiException {
        String text = update.getMessage().getText().trim();
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String username = update.getMessage().getFrom().getUserName();

        deletePreviousMessage(chatId, telegramId);

        SendMessage response;

        // --- Команды ---
        if (text.startsWith("/")) {
            // Любая команда прерывает ввод в профиле
            userService.clearProfileEditField(telegramId);

            response = switch (text) {
                case "/start" -> {
                    userService.clearWaitingForTaskNumber(telegramId);
                    userService.clearWaitingForFile(telegramId);
                    yield userCommandHandler.handleStartCommand(chatId, telegramId, username);
                }
                case "/help" -> userCommandHandler.handleHelpCommand(chatId);
                case "/tasks" -> userCommandHandler.handleMyTasksCommand(chatId, telegramId);
                case "/profile" -> profileCommandHandler.handleProfileCommand(chatId, telegramId);
                case "/pending" -> {
                    if (userService.isBoss(telegramId)) {
                        yield userCommandHandler.handlePendingRequests(chatId, telegramId);
                    } else {
                        yield new SendMessage(chatId.toString(), "Доступ запрещен. Эта команда доступна только руководителям.");
                    }
                }
                default -> new SendMessage(chatId.toString(),
                        "Команда не распознана. Используйте /help для списка доступных команд.");
            };
            sendAndTrack(chatId, telegramId, response);
            return;
        }

        // --- Регистрация ---
        if (!userService.existsByTelegramId(telegramId)) {
            response = userCommandHandler.handleRegistrationStep(chatId, telegramId, text);
            sendAndTrack(chatId, telegramId, response);
            return;
        }

        // --- Редактирование профиля ---
        if (userService.isEditingProfile(telegramId)) {
            response = profileCommandHandler.handleTextInput(chatId, telegramId, text);
            sendAndTrack(chatId, telegramId, response);
            return;
        }

        // --- Причина продления ---
        if (userService.isWaitingForExtensionReason(telegramId)) {
            response = userCommandHandler.handleExtensionReasonInput(chatId, telegramId, text);
            sendAndTrack(chatId, telegramId, response);
            return;
        }

        // --- Номер задачи ---
        if (userService.isWaitingForTaskNumber(telegramId)) {
            response = userCommandHandler.handleTaskSelection(chatId, telegramId, text);
            sendAndTrack(chatId, telegramId, response);
            return;
        }

        // --- Текстовый результат ---
        if (userService.isWaitingForFile(telegramId)) {
            response = userCommandHandler.handleTextResultSubmission(chatId, telegramId, text);
            sendAndTrack(chatId, telegramId, response);
            return;
        }

        // --- Неизвестно ---
        response = new SendMessage(chatId.toString(), "Команда не распознана. Используйте /help.");
        sendAndTrack(chatId, telegramId, response);
    }

    // --- УТИЛИТЫ ---
    private void deletePreviousMessage(Long chatId, Long telegramId) {
        Integer lastMsgId = userService.getLastMessageId(telegramId);
        if (lastMsgId != null) {
            deleteMessageSilently(chatId, lastMsgId);
            userService.setLastMessageId(telegramId, null);
        }
    }

    private void deleteMessageSilently(Long chatId, Integer messageId) {
        try {
            execute(new DeleteMessage(chatId.toString(), messageId));
            log.info("Deleted message {} in chat {}", messageId, chatId);
        } catch (TelegramApiException e) {
            log.warn("Failed to delete message {}: {}", messageId, e.getMessage());
        }
    }

    private void sendAndTrack(Long chatId, Long telegramId, SendMessage response) throws TelegramApiException {
        if (response == null) {
            log.warn("No response to send for telegramId {}", telegramId);
            return;
        }
        SendChatAction action = new SendChatAction();
        action.setChatId(chatId);
        action.setAction(ActionType.TYPING);
        execute(action);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        deletePreviousMessage(chatId, telegramId);
        Message sent = execute(response);
        if (sent != null && sent.getMessageId() != null) {
            userService.setLastMessageId(telegramId, sent.getMessageId());
            log.info("Sent message {} to telegramId {}", sent.getMessageId(), telegramId);
        }
    }
}