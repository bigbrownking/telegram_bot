package org.info.tg_bot.bot.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.handlers.UserCommandHandler;
import org.info.tg_bot.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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
            // 1️⃣ Обработка callback-кнопок
            if (update.hasCallbackQuery()) {
                CallbackQuery callback = update.getCallbackQuery();
                String callbackData = callback.getData();
                String callbackQueryId = callback.getId();
                Long chatId = callback.getMessage().getChatId();
                Long telegramId = callback.getFrom().getId();

                if (!userService.existsByTelegramId(telegramId)) {
                    SendMessage response = userCommandHandler.handleRegistrationStep(chatId, telegramId, callbackData);
                    execute(response);
                    execute(new AnswerCallbackQuery(callbackQueryId)); // Подтверждаем callback
                    return;
                }

                SendMessage response = userCommandHandler.handleCallback(chatId, telegramId, callbackData);
                execute(response);
                execute(new AnswerCallbackQuery(callbackQueryId)); // Подтверждаем callback
                return;
            }

            // 2️⃣ Обработка документов (файлов)
            if (update.hasMessage() && update.getMessage().hasDocument()) {
                Long chatId = update.getMessage().getChatId();
                Long telegramId = update.getMessage().getFrom().getId();

                if (userService.isWaitingForFile(telegramId)) {
                    org.telegram.telegrambots.meta.api.objects.Document tgDocument = update.getMessage().getDocument();

                    SendMessage response = userCommandHandler.handleFileUpload(chatId, telegramId, tgDocument, this);
                    execute(response);
                } else {
                    execute(new SendMessage(chatId.toString(), "⚠️ Сейчас не ожидается загрузка файла."));
                }
                return;
            }

            // 3️⃣ Обработка текстовых сообщений
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText().trim();
                Long chatId = update.getMessage().getChatId();
                Long telegramId = update.getMessage().getFrom().getId();
                String username = update.getMessage().getFrom().getUserName();

                // Проверка на команды - команды ВСЕГДА имеют приоритет
                if (text.startsWith("/")) {
                    SendMessage response = switch (text) {
                        case "/start" -> {
                            userService.clearWaitingForTaskNumber(telegramId);
                            userService.clearWaitingForFile(telegramId);
                            yield userCommandHandler.handleStartCommand(chatId, telegramId, username);
                        }
                        case "/help" -> {
                            userService.clearWaitingForTaskNumber(telegramId);
                            yield userCommandHandler.handleHelpCommand(chatId);
                        }
                        case "/mytasks" -> {
                            userService.clearWaitingForTaskNumber(telegramId);
                            yield userCommandHandler.handleMyTasksCommand(chatId, telegramId);
                        }
                        default -> new SendMessage(chatId.toString(),
                                "❓ Команда не распознана. Используйте /help для списка доступных команд.");
                    };
                    execute(response);
                    return;
                }

                if (userCommandHandler.isWaitingForExtensionReason(telegramId)) {
                    SendMessage response = userCommandHandler.handleExtensionReasonInput(chatId, telegramId, text);
                    execute(response);
                    return;
                }

                if (userService.isWaitingForTaskNumber(telegramId)) {
                    SendMessage response = userCommandHandler.handleTaskSelection(chatId, telegramId, text);
                    execute(response);
                    return;
                }

                // Если не команда и не ожидается ввод - неизвестная команда
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
