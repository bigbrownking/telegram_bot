package org.info.tg_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.common.AdminBot;
import org.info.tg_bot.bot.common.UserBot;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Service
public class NotificationService {

    private final UserBot userBot;
    private final AdminBot adminBot;

    public NotificationService(@Lazy UserBot userBot, @Lazy AdminBot adminBot) {
        this.userBot = userBot;
        this.adminBot = adminBot;
    }

    public void notifyUser(Long telegramId, String text) {
        sendMessage(userBot, telegramId, text);
    }

    public void notifyAdmin(Long adminTelegramId, String messageText, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(adminTelegramId.toString());
            message.setText(messageText);
            message.setParseMode("HTML");
            message.setReplyMarkup(keyboard);

            adminBot.execute(message);
            log.info("✅ Notification with keyboard sent to admin {}", adminTelegramId);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка при отправке уведомления администратору {}: {}", adminTelegramId, e.getMessage());
        }
    }

    private void sendMessage(TelegramLongPollingBot bot, Long telegramId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(telegramId.toString());
            message.setText(text);
            message.setParseMode("HTML");

            bot.execute(message);
        } catch (Exception e) {
            log.error("❌ Ошибка отправки уведомления пользователю {}: {}", telegramId, e.getMessage());
        }
    }
}