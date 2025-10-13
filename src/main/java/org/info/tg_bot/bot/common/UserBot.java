package org.info.tg_bot.bot.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.handlers.UserCommandHandler;
import org.info.tg_bot.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserBot extends TelegramLongPollingBot {

    private final UserCommandHandler userCommandHandler;
    private final UserService userService;

    @Value("${tg.user.token}")
    private String botToken;

    @Value("${tg.user.username}")
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
            if (update.hasCallbackQuery()) {
                CallbackQuery callback = update.getCallbackQuery();
                String callbackData = callback.getData();
                Long chatId = callback.getMessage().getChatId();
                Long telegramId = callback.getFrom().getId();

                if (!userService.existsByTelegramId(telegramId)) {
                    SendMessage response = userCommandHandler.handleRegistrationStep(chatId, telegramId, callbackData);
                    execute(response);
                    return;
                }

                SendMessage response = userCommandHandler.handleCallback(chatId, telegramId, callbackData);
                execute(response);
                return;
            }

            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                Long chatId = update.getMessage().getChatId();
                Long telegramId = update.getMessage().getFrom().getId();
                String username = update.getMessage().getFrom().getUserName();

                if (userService.isWaitingForTaskNumber(telegramId)) {
                    SendMessage response = userCommandHandler.handleTaskSelection(chatId, telegramId, text);
                    execute(response);
                    return;
                }
                if (userCommandHandler.isWaitingForExtensionReason(telegramId)) {
                    SendMessage response = userCommandHandler.handleExtensionReasonInput(chatId, telegramId, text);
                    execute(response);
                    return;
                }

                SendMessage response = switch (text) {
                    case "/start" -> userCommandHandler.handleStartCommand(chatId, telegramId, username);
                    case "/help" -> userCommandHandler.handleHelpCommand(chatId);
                    case "/mytasks" -> userCommandHandler.handleMyTasksCommand(chatId, telegramId);
                    default -> new SendMessage(chatId.toString(),
                            "❓ Команда не распознана. Используйте /help для списка доступных команд.");
                };
                execute(response);
            }
        } catch (TelegramApiException e) {
            log.error("❌ Error sending message", e);
        } catch (Exception e) {
            log.error("❌ Unexpected error", e);
        }
    }
}
