package org.info.tg_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.common.UserBot;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Service
public class NotificationService {

    private final UserBot userBot;

    public NotificationService(@Lazy UserBot userBot) {
        this.userBot = userBot;
    }

    public void notifyUser(Long telegramId, String messageText) {
        try {
            SendMessage message = new SendMessage(telegramId.toString(), messageText);
            userBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Failed to send notification to user {}", telegramId, e);
        }
    }
}
