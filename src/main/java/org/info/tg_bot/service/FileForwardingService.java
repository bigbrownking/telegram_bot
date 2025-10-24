package org.info.tg_bot.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.common.AdminBot;
import org.info.tg_bot.bot.common.UserBot;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.ActionCategory;
import org.info.tg_bot.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.InputStream;
import java.util.Map;

@Slf4j
@Setter
@Service
@RequiredArgsConstructor
public class FileForwardingService {

    private UserBot userBot;
    private AdminBot adminBot;
    private final DocumentService documentService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @PostConstruct
    public void init() {
        if (userBot == null) {
            log.warn("⚠️ UserBot not set in FileForwardingService - file downloads will fail until set");
        }
        if (adminBot == null) {
            log.warn("⚠️ AdminBot not set in FileForwardingService - file forwarding will fail until set");
        }
    }

    public void sendDocumentToAdmin(Long adminTelegramId, String caption, String fileId, String fileName, String regNumber,
                                    InlineKeyboardMarkup inlineKeyboardMarkup) {
        User admin = userService.findByTelegramId(adminTelegramId).orElse(null);
        String tgName = "";
        if(admin != null) {
            tgName = admin.getTelegramUsername();
        }

        Document document = documentService.findByRegNumber(regNumber).orElse(null);
        User executor = null;
        Long entityId = null;
        if(document != null){
            entityId = document.getId();
            executor = document.getExecutor();
        }
        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(fileId);
            File telegramFile = userBot.execute(getFile);

            if (telegramFile == null || telegramFile.getFilePath() == null) {
                log.error("❌ Failed to retrieve file path for fileId: {}", fileId);
                auditLogService.logAction(
                        executor,
                        "SEND_DOCUMENT_TO_ADMIN",
                        ActionCategory.DOCUMENT,
                        "File",
                        entityId,
                        "Failed to retrieve file path",
                        null,
                        null,
                        "Telegram Bot",
                        tgName,
                        false,
                        "Failed to retrieve file path",
                        Map.of("adminTelegramId", adminTelegramId.toString(), "fileId", fileId, "fileName", fileName)
                        );
                return;
            }

            log.info("📦 File path retrieved: {}", telegramFile.getFilePath());

            try (InputStream fileStream = userBot.downloadFileAsStream(telegramFile.getFilePath())) {
                if (fileStream == null) {
                    log.error("❌ Failed to download file stream for fileId: {}", fileId);
                    auditLogService.logAction(
                            executor,
                            "SEND_DOCUMENT_TO_ADMIN",
                            ActionCategory.DOCUMENT,
                            "File",
                            entityId,
                            "Failed to download file stream",
                            null,
                            null,
                            "Telegram Bot",
                            tgName,
                            false,
                            "Failed to download file stream",
                            Map.of("adminTelegramId", adminTelegramId.toString(), "fileId", fileId, "fileName", fileName)
                            );
                    return;
                }

                SendDocument sendDocument = new SendDocument();
                sendDocument.setChatId(adminTelegramId.toString());
                sendDocument.setCaption(caption);
                sendDocument.setParseMode("HTML");
                sendDocument.setDocument(new InputFile(fileStream, fileName));
                sendDocument.setReplyMarkup(inlineKeyboardMarkup);

                adminBot.execute(sendDocument);
                log.info("📄 File '{}' sent to admin {} via AdminBot with action buttons", fileName, adminTelegramId);

                auditLogService.logAction(
                        executor,
                        "SEND_DOCUMENT_TO_ADMIN",
                        ActionCategory.DOCUMENT,
                        "File",
                        entityId,
                        "Document sent to admin",
                        null,
                        null,
                        "Telegram Bot",
                        tgName,
                        true,
                        null,
                        Map.of("adminTelegramId", adminTelegramId.toString(), "fileId", fileId, "fileName", fileName, "regNumber", regNumber)
                        );
            }

        } catch (TelegramApiException e) {
            log.error("❌ Telegram API error while sending file to admin {}: {}", adminTelegramId, e.getMessage(), e);
            auditLogService.logAction(
                    executor,
                    "SEND_DOCUMENT_TO_ADMIN",
                    ActionCategory.DOCUMENT,
                    "File",
                    entityId,
                    "Failed to send document to admin due to Telegram API error",
                    null,
                    null,
                    "Telegram Bot",
                    tgName,
                    false,
                    e.getMessage(),
                    Map.of("adminTelegramId", adminTelegramId.toString(), "fileId", fileId, "fileName", fileName, "error", e.getMessage())
                    );
        } catch (Exception e) {
            log.error("❌ Unexpected error while forwarding file to admin {}: {}", adminTelegramId, e.getMessage(), e);
            auditLogService.logAction(
                    executor,
                    "SEND_DOCUMENT_TO_ADMIN",
                    ActionCategory.DOCUMENT,
                    "File",
                    entityId,
                    "Failed to send document to admin due to unexpected error",
                    null,
                    null,
                    "Telegram Bot",
                    tgName,
                    false,
                    e.getMessage(),
                    Map.of("adminTelegramId", adminTelegramId.toString(), "fileId", fileId, "fileName", fileName, "error", e.getMessage())
                    );
        }
    }
}