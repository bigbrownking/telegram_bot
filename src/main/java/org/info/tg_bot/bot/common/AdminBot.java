package org.info.tg_bot.bot.common;

import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.handlers.AdminCommandHandler;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.CancellationType;
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
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class AdminBot extends TelegramLongPollingBot {

    private final AdminCommandHandler adminCommandHandler;
    private final UserService userService;

    @Value("${tg.admin.token}")
    private String botToken;

    @Value("${tg.admin.username}")
    private String botUsername;

    public AdminBot(
            AdminCommandHandler adminCommandHandler,
            UserService userService,
            DefaultBotOptions options,
            @Value("${tg.admin.token}") String botToken,
            @Value("${tg.admin.username}") String botUsername
    ) {
        super(options, botToken);
        this.adminCommandHandler = adminCommandHandler;
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

            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty() || !userOpt.get().isAdmin()) {
                sendAndTrack(chatId, telegramId, new SendMessage(chatId.toString(), "У вас нет доступа к этому боту."));
                return;
            }

            // CALLBACK
            if (update.hasCallbackQuery()) {
                handleCallback(update, chatId, telegramId);
                return;
            }


            // TEXT
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update, chatId, telegramId);
                return;
            }

            // FILE
            if (update.hasMessage() && update.getMessage().hasDocument()) {
                handleFileUpload(update, chatId, telegramId);
                return;
            }

        } catch (TelegramApiException e) {
            log.error("Telegram API error: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error in AdminBot: {}", e.getMessage(), e);
        }
    }

    private void handleCallback(Update update, Long chatId, Long telegramId) throws TelegramApiException {
        CallbackQuery callback = update.getCallbackQuery();
        String callbackData = callback.getData();
        String callbackQueryId = callback.getId();
        Integer callbackMessageId = callback.getMessage().getMessageId();

        deleteMessageSilently(chatId, callbackMessageId);

        deletePreviousMessage(chatId, telegramId);

        SendMessage response;

        // --- Одобрение/отклонение ---
        if (callbackData.startsWith("approve_extend:")) {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            response = adminCommandHandler.handleApproveExtendRequest(chatId, requestId, telegramId);
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        if (callbackData.startsWith("reject_extend:")) {
            Long requestId = Long.parseLong(callbackData.split(":")[1]);
            response = adminCommandHandler.handleRejectExtendRequest(chatId, requestId, telegramId);
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        // --- Закрытие без исполнения ---
        if (callbackData.startsWith("close_without_execution:")) {
            String regNumber = callbackData.split(":")[1];
            response = createCancellationTypeKeyboard(chatId, regNumber);
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        if (callbackData.equals("cancel_operation")) {
            userService.clearWaitingForCancelDocumentNumber(telegramId);
            userService.clearPendingCancellation(telegramId);
            userService.clearPendingCancellation(chatId);
            response = new SendMessage(chatId.toString(), "Операция отменена. Вы вернулись в главное меню.");
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        if (callbackData.startsWith("cancel_reason:")) {
            String[] parts = callbackData.split(":");
            String regNumber = parts[1];
            CancellationType type = CancellationType.valueOf(parts[2]);

            if (type == CancellationType.OTHER) {
                userService.setPendingCancellation(chatId, regNumber, type.name());
                response = new SendMessage(chatId.toString(),
                        "Пожалуйста, введите причину закрытия поручения №" + regNumber + ":");
                sendAndTrack(chatId, telegramId, response);
            } else {
                response = adminCommandHandler.handleCloseWithoutExecution(
                        chatId, regNumber, telegramId, type, type.getDisplayName());
                sendAndTrack(chatId, telegramId, response);
            }
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        // --- Завершение ---
        if (callbackData.startsWith("task_completed:")) {
            String regNumber = callbackData.split(":")[1];
            response = adminCommandHandler.handleTaskCompleted(chatId, regNumber, telegramId, null);
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        // --- Доработка ---
        if (callbackData.startsWith("send_for_revision:")) {
            String regNumber = callbackData.split(":")[1];
            response = adminCommandHandler.handleSendForRevision(chatId, regNumber, telegramId);
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        // --- Фильтр задач ---
        if (callbackData.startsWith("tasks_filter:")) {
            String statusFilter = callbackData.substring("tasks_filter:".length());
            response = adminCommandHandler.handleTasksFilter(chatId, telegramId, statusFilter);
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        if (callbackData.equals("tasks_back")) {
            response = adminCommandHandler.handleTasksCommand(chatId, telegramId, null);
            sendAndTrack(chatId, telegramId, response);
            execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        // Неизвестный callback
        response = new SendMessage(chatId.toString(), "Неизвестное действие.");
        sendAndTrack(chatId, telegramId, response);
        execute(new AnswerCallbackQuery(callbackQueryId));
    }


    private void handleTextMessage(Update update, Long chatId, Long telegramId) throws TelegramApiException {
        String text = update.getMessage().getText().trim();
        String username = update.getMessage().getFrom().getUserName();

        deletePreviousMessage(chatId, telegramId);

        SendMessage response;

        // --- Ожидание номера для отмены ---
        if (userService.isWaitingForCancelDocumentNumber(telegramId)) {
            response = adminCommandHandler.handleCancelDocumentNumberInput(chatId, telegramId, text);
            sendAndTrack(chatId, telegramId, response);
            return;
        }

        // --- Причина отмены ---
        if (userService.isWaitingForCancellationReason(chatId)) {
            response = adminCommandHandler.handleCancelReasonInput(chatId, telegramId, text);
            sendAndTrack(chatId, telegramId, response);
            return;
        }

        // --- Причина доработки ---
        if (userService.isWaitingForRevisionReason(telegramId)) {
            response = adminCommandHandler.handleRevisionReasonInput(chatId, telegramId, text);
            sendAndTrack(chatId, telegramId, response);
            return;
        }

        // --- Новая дата ---
        if (userService.isWaitingForNewDate(telegramId)) {
            response = adminCommandHandler.handleNewExecutionDate(chatId, telegramId, text);
            sendAndTrack(chatId, telegramId, response);
            return;
        }

        // --- Обратная связь при завершении ---
        if (userService.isWaitingForCompletionFeedback(telegramId)) {
            Optional<String> regNumberOpt = userService.consumeWaitingForCompletionFeedback(telegramId);
            if (regNumberOpt.isPresent()) {
                response = adminCommandHandler.handleTaskCompleted(chatId, regNumberOpt.get(), telegramId, text);
                sendAndTrack(chatId, telegramId, response);
                return;
            }
        }

        // --- Команды ---
        if (text.startsWith("/tasks")) {
            String regNumber = text.contains(" ") ? text.substring(text.indexOf(" ") + 1).trim() : null;
            response = adminCommandHandler.handleTasksCommand(chatId, telegramId, regNumber);
        } else {
            response = switch (text) {
                case "/start" -> adminCommandHandler.handleStartCommand(chatId, telegramId, username);
                case "/help" -> adminCommandHandler.handleHelpCommand(chatId);
                case "/upload" -> adminCommandHandler.handleUploadCommand(chatId);
                case "/pending" -> adminCommandHandler.handlePendingRequests(chatId, telegramId);
                case "/cancel" -> adminCommandHandler.handleCancelCommand(chatId, telegramId);
                default -> new SendMessage(chatId.toString(),
                        "Команда не распознана. Используйте /help.");
            };
        }
        sendAndTrack(chatId, telegramId, response);
    }

    private void handleFileUpload(Update update, Long chatId, Long telegramId) throws TelegramApiException {
        deletePreviousMessage(chatId, telegramId);

        String fileName = update.getMessage().getDocument().getFileName();
        String fileId = update.getMessage().getDocument().getFileId();

        SendMessage response = adminCommandHandler.handleExcelUpload(chatId, telegramId, fileName, fileId, this);
        sendAndTrack(chatId, telegramId, response);
    }

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

    private SendMessage createCancellationTypeKeyboard(Long chatId, String regNumber) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (CancellationType type : CancellationType.values()) {
            InlineKeyboardButton button = new InlineKeyboardButton(type.getDisplayName());
            button.setCallbackData("cancel_reason:" + regNumber + ":" + type.name());
            rows.add(List.of(button));
        }

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage(chatId.toString(),
                "Выберите причину закрытия поручения №" + regNumber + ":");
        message.setReplyMarkup(keyboard);
        return message;
    }
}