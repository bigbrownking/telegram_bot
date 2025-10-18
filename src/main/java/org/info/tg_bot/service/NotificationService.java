package org.info.tg_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.common.AdminBot;
import org.info.tg_bot.bot.common.UserBot;
import org.info.tg_bot.model.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private UserBot userBot;
    private AdminBot adminBot;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @Autowired
    public void setUserBot(@Lazy UserBot userBot) {
        this.userBot = userBot;
    }

    @Autowired
    public void setAdminBot(@Lazy AdminBot adminBot) {
        this.adminBot = adminBot;
    }
    public void notifyUser(Long telegramId, String text, InlineKeyboardMarkup keyboard) {
        User user = userService.findByTelegramId(telegramId).orElse(null);
        String username = "";
        if(user != null){
            username = user.getTelegramUsername();
        }
        try {
            SendMessage message = new SendMessage();
            message.setChatId(telegramId.toString());
            message.setText(text);
            message.setParseMode("HTML");
            message.setReplyMarkup(keyboard);

            userBot.execute(message);
            log.info("✅ Notification with keyboard sent to user {}", telegramId);


            auditLogService.logAction(
                    user,
                    "SEND_USER_NOTIFICATION",
                    org.info.tg_bot.model.enums.ActionCategory.NOTIFICATION,
                    "Notification",
                    null,
                    "Notification sent to user",
                    null,
                    null,
                    "Telegram Bot",
                    username,
                    true,
                    null,
                    Map.of("telegramId", telegramId.toString(), "hasKeyboard", keyboard != null)
                    );
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка при отправке уведомления пользователю {}: {}", telegramId, e.getMessage());

            auditLogService.logAction(
                    user,
                    "SEND_USER_NOTIFICATION",
                    org.info.tg_bot.model.enums.ActionCategory.NOTIFICATION,
                    "Notification",
                    null,
                    "Failed to send notification to user",
                    null,
                    null,
                    "Telegram Bot",
                    username,
                    false,
                    e.getMessage(),
                    Map.of("telegramId", telegramId.toString(), "error", e.getMessage())
                    );
        }
    }

    public void notifyAdmin(Long adminTelegramId, String messageText, InlineKeyboardMarkup keyboard) {
        User admin = userService.findByTelegramId(adminTelegramId).orElse(null);
        String username = "";
        if(admin!= null){
            username = admin.getTelegramUsername();
        }
        try {
            SendMessage message = new SendMessage();
            message.setChatId(adminTelegramId.toString());
            message.setText(messageText);
            message.setParseMode("HTML");
            message.setReplyMarkup(keyboard);

            adminBot.execute(message);
            log.info("✅ Notification with keyboard sent to admin {}", adminTelegramId);

            auditLogService.logAction(
                    admin,
                    "SEND_ADMIN_NOTIFICATION",
                    org.info.tg_bot.model.enums.ActionCategory.NOTIFICATION,
                    "Notification",
                    null,
                    "Notification sent to admin",
                    null,
                    null,
                    "Telegram Bot",
                    username,
                    true,
                    null,
                    Map.of("telegramId", adminTelegramId.toString(), "hasKeyboard", keyboard != null)

                    );
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка при отправке уведомления администратору {}: {}", adminTelegramId, e.getMessage());

            auditLogService.logAction(
                    admin,
                    "SEND_ADMIN_NOTIFICATION",
                    org.info.tg_bot.model.enums.ActionCategory.NOTIFICATION,
                    "Notification",
                    null,
                    "Failed to send notification to admin",
                    null,
                    null,
                    "Telegram Bot",
                    username,
                    false,
                    e.getMessage(),
                    Map.of("telegramId", adminTelegramId.toString(), "error", e.getMessage())
                    );
        }
    }
}