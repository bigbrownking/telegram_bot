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
                execute(new SendMessage(chatId.toString(), "🚫 У вас нет доступа к этому боту."));
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
                    execute(adminCommandHandler.handleApproveExtendRequest(chatId, requestId, telegramId));
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }

                if (callbackData.startsWith("reject_extend:")) {
                    Long requestId = Long.parseLong(callbackData.split(":")[1]);
                    execute(adminCommandHandler.handleRejectExtendRequest(chatId, requestId, telegramId));
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

                    SendMessage msg = new SendMessage(
                            chatId.toString(),
                            "❓ Выберите причину закрытия поручения №" + regNumber + ":"
                    );
                    msg.setReplyMarkup(keyboard);

                    execute(msg);
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }

                if (callbackData.startsWith("cancel_reason:")) {
                    String[] parts = callbackData.split(":");
                    String regNumber = parts[1];
                    CancellationType type = CancellationType.valueOf(parts[2]);

                    if (type == CancellationType.OTHER) {
                        userService.setPendingCancellation(chatId, regNumber, type.name());

                        SendMessage askReason = new SendMessage(
                                chatId.toString(),
                                "✏ Пожалуйста, введите причину закрытия поручения №" + regNumber + ":"
                        );
                        execute(askReason);
                        execute(new AnswerCallbackQuery(callbackQueryId));
                    } else {
                        execute(adminCommandHandler.handleCloseWithoutExecution(
                                chatId, regNumber, telegramId, type, type.getDisplayName()
                        ));
                        execute(new AnswerCallbackQuery(callbackQueryId));
                    }
                    return;
                }

                if (callbackData.startsWith("task_completed:")) {
                    String regNumber = callbackData.split(":")[1];
                    execute(adminCommandHandler.handleTaskCompleted(chatId, regNumber, telegramId));
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }

                if (callbackData.startsWith("send_for_revision:")) {
                    String regNumber = callbackData.split(":")[1];
                    execute(adminCommandHandler.handleSendForRevision(chatId, regNumber, telegramId));
                    execute(new AnswerCallbackQuery(callbackQueryId));
                    return;
                }
            }

            // Обработка текстовых сообщений
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText().trim();
                String username = update.getMessage().getFrom().getUserName();

                if (userService.isWaitingForCancellationReason(chatId)) {
                    Optional<String> dataOpt = userService.consumePendingCancellation(chatId);
                    if (dataOpt.isPresent()) {
                        String[] parts = dataOpt.get().split(":");
                        String regNumber = parts[0];
                        CancellationType type = CancellationType.valueOf(parts[1]);

                        execute(adminCommandHandler.handleCloseWithoutExecution(
                                chatId, regNumber, telegramId, type, text
                        ));
                        return;
                    }
                }

                if (adminCommandHandler.isWaitingForNewDate(telegramId)) {
                    execute(adminCommandHandler.handleNewExecutionDate(chatId, telegramId, text));
                    return;
                }

                if (adminCommandHandler.isWaitingForRevisionReason(telegramId)) {
                    execute(adminCommandHandler.handleRevisionReasonInput(chatId, telegramId, text));
                    return;
                }

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

            if (update.hasMessage() && update.getMessage().hasDocument()) {
                String fileName = update.getMessage().getDocument().getFileName();
                String fileId = update.getMessage().getDocument().getFileId();
                execute(adminCommandHandler.handleExcelUpload(chatId, telegramId, fileName, fileId, this));
            }

        } catch (TelegramApiException e) {
            log.error("Error sending message: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
        }
    }
}
