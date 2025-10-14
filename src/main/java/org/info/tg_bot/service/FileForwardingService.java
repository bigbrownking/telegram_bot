package org.info.tg_bot.service;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.common.AdminBot;
import org.info.tg_bot.bot.common.UserBot;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
public class FileForwardingService {

    @Setter
    private UserBot userBot;

    @Setter
    private AdminBot adminBot;

    @PostConstruct
    public void init() {
        if (userBot == null) {
            log.warn("⚠️ UserBot not set in FileForwardingService - file downloads will fail until set");
        }
        if (adminBot == null) {
            log.warn("⚠️ AdminBot not set in FileForwardingService - file forwarding will fail until set");
        }
    }

    public void sendDocumentToAdmin(Long adminTelegramId, String caption, String fileId, String fileName, String regNumber) {
        if (adminBot == null) {
            log.error("❌ AdminBot is not initialized. Cannot send file.");
            return;
        }

        if (userBot == null) {
            log.error("❌ UserBot is not initialized. Cannot download file.");
            return;
        }

        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(fileId);
            File telegramFile = userBot.execute(getFile);

            if (telegramFile == null || telegramFile.getFilePath() == null) {
                log.error("❌ Failed to retrieve file path for fileId: {}", fileId);
                return;
            }

            log.info("📦 File path retrieved: {}", telegramFile.getFilePath());

            try (InputStream fileStream = userBot.downloadFileAsStream(telegramFile.getFilePath())) {
                if (fileStream == null) {
                    log.error("❌ Failed to download file stream for fileId: {}", fileId);
                    return;
                }

                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                keyboard.setKeyboard(List.of(
                        List.of(
                                InlineKeyboardButton.builder()
                                        .text("Закрыть без исполнения")
                                        .callbackData("close_without_execution:" + regNumber)
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("Поручение исполнено")
                                        .callbackData("task_completed:" + regNumber)
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("Отправить на доработку")
                                        .callbackData("send_for_revision:" + regNumber)
                                        .build()
                        )
                ));

                SendDocument sendDocument = new SendDocument();
                sendDocument.setChatId(adminTelegramId.toString());
                sendDocument.setCaption(caption);
                sendDocument.setParseMode("HTML");
                sendDocument.setDocument(new InputFile(fileStream, fileName));
                sendDocument.setReplyMarkup(keyboard);

                adminBot.execute(sendDocument);
                log.info("📄 File '{}' sent to admin {} via AdminBot with action buttons", fileName, adminTelegramId);
            }

        } catch (TelegramApiException e) {
            log.error("❌ Telegram API error while sending file to admin {}: {}", adminTelegramId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error while forwarding file to admin {}: {}", adminTelegramId, e.getMessage(), e);
        }
    }
}