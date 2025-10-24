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
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
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

            Optional<User> user = userService.findByTelegramId(telegramId);
            if (user.isEmpty() || !user.get().isAdmin()) {
                SendMessage response = new SendMessage(chatId.toString(), "🚫 У вас нет доступа к этому боту.");
                sendAndTrack(chatId, telegramId, response);
                return;
            }

            // ======================================================
            // ✅ Обработка callback-запросов
            // ======================================================
            if (update.hasCallbackQuery()) {
                String callbackData = update.getCallbackQuery().getData();
                String callbackQueryId = update.getCallbackQuery().getId();

                if (callbackData.startsWith("approve_extend:")) {
                    Long requestId = Long.parseLong(callbackData.split(":")[1]);
                    SendMessage response = adminCommandHandler.handleApproveExtendRequest(chatId, requestId, telegramId);
                    sendAndTrack(chatId, telegramId, response);
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }

                if (callbackData.startsWith("reject_extend:")) {
                    Long requestId = Long.parseLong(callbackData.split(":")[1]);
                    SendMessage response = adminCommandHandler.handleRejectExtendRequest(chatId, requestId, telegramId);
                    sendAndTrack(chatId, telegramId, response);
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }

                if (callbackData.startsWith("close_without_execution:")) {
                    String regNumber = callbackData.split(":")[1];

                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();

                    for (CancellationType type : CancellationType.values()) {
                        InlineKeyboardButton button = new InlineKeyboardButton();
                        button.setText(type.getDisplayName());
                        button.setCallbackData("cancel_reason:" + regNumber + ":" + type.name());
                        rows.add(List.of(button));
                    }

                    keyboard.setKeyboard(rows);

                    SendMessage response = new SendMessage(
                            chatId.toString(),
                            "❓ Выберите причину закрытия поручения №" + regNumber + ":"
                    );
                    response.setReplyMarkup(keyboard);
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
                        SendMessage response = new SendMessage(
                                chatId.toString(),
                                "✏ Пожалуйста, введите причину закрытия поручения №" + regNumber + ":"
                        );
                        sendAndTrack(chatId, telegramId, response);
                        execute(new AnswerCallbackQuery(callbackQueryId));
                    } else {
                        SendMessage response = adminCommandHandler.handleCloseWithoutExecution(
                                chatId, regNumber, telegramId, type, type.getDisplayName()
                        );
                        sendAndTrack(chatId, telegramId, response);
                        execute(new AnswerCallbackQuery(callbackQueryId));
                    }
                    return;
                }

                if (callbackData.startsWith("task_completed:")) {
                    String regNumber = callbackData.split(":")[1];
                    SendMessage response = adminCommandHandler.handleTaskCompleted(chatId, regNumber, telegramId, null);
                    sendAndTrack(chatId, telegramId, response);
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }

                if (callbackData.startsWith("send_for_revision:")) {
                    String regNumber = callbackData.split(":")[1];
                    SendMessage response = adminCommandHandler.handleSendForRevision(chatId, regNumber, telegramId);
                    sendAndTrack(chatId, telegramId, response);
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }

                if (callbackData.startsWith("tasks_filter:")) {
                    String statusFilter = callbackData.substring("tasks_filter:".length());
                    SendMessage response = adminCommandHandler.handleTasksFilter(chatId, telegramId, statusFilter);
                    sendAndTrack(chatId, telegramId, response);
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }

                if (callbackData.equals("tasks_back")) {
                    SendMessage response = adminCommandHandler.handleTasksCommand(chatId, telegramId, null);
                    sendAndTrack(chatId, telegramId, response);
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }
            }

            // ======================================================
            // ✅ Обработка текстовых сообщений
            // ======================================================
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText().trim();
                String username = update.getMessage().getFrom().getUserName();

                // Обработка номера документа для отмены
                if (userService.isWaitingForCancelDocumentNumber(telegramId)) {
                    SendMessage response = adminCommandHandler.handleCancelDocumentNumberInput(chatId, telegramId, text);
                    sendAndTrack(chatId, telegramId, response);
                    return;
                }

                // Обработка причины отмены
                if (userService.isWaitingForCancellationReason(chatId)) {
                    SendMessage response = adminCommandHandler.handleCancelReasonInput(chatId, telegramId, text);
                    sendAndTrack(chatId, telegramId, response);
                    return;
                }

                // Обработка причины доработки
                if (userService.isWaitingForRevisionReason(telegramId)) {
                    SendMessage response = adminCommandHandler.handleRevisionReasonInput(chatId, telegramId, text);
                    sendAndTrack(chatId, telegramId, response);
                    return;
                }

                // Обработка даты
                if (userService.isWaitingForNewDate(telegramId)) {
                    SendMessage response = adminCommandHandler.handleNewExecutionDate(chatId, telegramId, text);
                    sendAndTrack(chatId, telegramId, response);
                    return;
                }

                // Обработка обратной связи при завершении
                if (userService.isWaitingForCompletionFeedback(telegramId)) {
                    Optional<String> regNumberOpt = userService.consumeWaitingForCompletionFeedback(telegramId);
                    if (regNumberOpt.isPresent()) {
                        SendMessage response = adminCommandHandler.handleTaskCompleted(chatId, regNumberOpt.get(), telegramId, text);
                        sendAndTrack(chatId, telegramId, response);
                        return;
                    }
                }

                // Обработка команд
                SendMessage response;
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
                                "❓ Команда не распознана. Используйте /help для списка доступных команд.");
                    };
                }
                sendAndTrack(chatId, telegramId, response);
                return;
            }

            // Обработка загрузки документов
            if (update.hasMessage() && update.getMessage().hasDocument()) {
                String fileName = update.getMessage().getDocument().getFileName();
                String fileId = update.getMessage().getDocument().getFileId();
                SendMessage response = adminCommandHandler.handleExcelUpload(chatId, telegramId, fileName, fileId, this);
                sendAndTrack(chatId, telegramId, response);
            }

        } catch (TelegramApiException e) {
            log.error("Error sending message: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
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