package org.info.tg_bot.bot.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.*;
import org.info.tg_bot.model.enums.ActionCategory;
import org.info.tg_bot.model.enums.CancellationType;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.info.tg_bot.service.*;
import org.info.tg_bot.service.AuditLogService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminCommandHandler {

    private final UserService userService;
    private final DocumentService documentService;
    private final DocumentParserService documentParserService;
    private final ExtensionRequestService extensionRequestService;
    private final NotificationService notificationService;
    private final ExecutionResultService executionResultService;
    private final DocumentCancellationService documentCancellationService;
    private final AuditLogService auditLogService;

    // --- Commands ---
    public SendMessage handleStartCommand(Long chatId, Long telegramId, String username) {
        String msg = String.format(
                "👋 Здравствуйте, %s!\n\n" +
                        "Вы авторизованы как администратор. " +
                        "Используйте /upload для загрузки Excel-файлов, /pending для просмотра запросов на продление, или /cancel для отмены документа.",
                username != null ? username : "администратор"
        );
        if (userService.existsByTelegramId(telegramId)) {
            User user = userService.findByTelegramId(telegramId).orElseThrow();
            auditLogService.logAction(
                    user,
                    "LOGIN",
                    ActionCategory.AUTH,
                    "Admin",
                    user.getId(),
                    "User logged in",
                    null,
                    null,
                    "Telegram Bot",
                    user.getTelegramUsername(),
                    true,
                    null,
                    null
            );
        }
        return new SendMessage(chatId.toString(), msg);
    }

    public SendMessage handleHelpCommand(Long chatId) {
        String help = """
                📘 Доступные команды:
                /start – начать работу
                /upload – загрузить Excel с поручениями
                /pending – просмотреть запросы на продление
                /tasks – просмотреть все загруженные поручения
                /cancel – отменить документ
                /help – показать это сообщение
                """;
        return new SendMessage(chatId.toString(), help);
    }

    public SendMessage handleUploadCommand(Long chatId) {
        return new SendMessage(chatId.toString(), "📤 Отправьте Excel-файл (.xlsx или .xls).");
    }

    public SendMessage handleCancelCommand(Long chatId, Long adminTelegramId) {
        userService.setWaitingForCancelDocumentNumber(adminTelegramId, true);
        return new SendMessage(chatId.toString(), "📝 Введите регистрационный номер документа для отмены:");
    }

    public SendMessage handleCancelDocumentNumberInput(Long chatId, Long adminTelegramId, String regNumber) {
        if (!userService.isWaitingForCancelDocumentNumber(adminTelegramId)) {
            return new SendMessage(chatId.toString(), "⚠ Нет активного запроса на отмену. Используйте /cancel.");
        }

        Optional<Document> documentOpt = documentService.findByRegNumber(regNumber);
        if (documentOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Документ с номером " + regNumber + " не найден.");
        }

        Document document = documentOpt.get();
        Optional<User> adminOpt = userService.findByTelegramId(adminTelegramId);
        if (adminOpt.isEmpty() || !document.getUploadedBy().getTelegramId().equals(adminTelegramId)) {
            userService.clearWaitingForCancelDocumentNumber(adminTelegramId);
            return new SendMessage(chatId.toString(), "⚠ У вас нет прав для отмены этого документа.");
        }

        userService.clearWaitingForCancelDocumentNumber(adminTelegramId);
        userService.setPendingCancellation(adminTelegramId, regNumber, CancellationType.OTHER.name());

        return new SendMessage(chatId.toString(),
                "💬 Пожалуйста, укажите причину отмены документа №" + regNumber + ":");
    }

    public SendMessage handleCancelReasonInput(Long chatId, Long adminTelegramId, String reason) {
        if (!userService.isWaitingForCancellationReason(adminTelegramId)) {
            return new SendMessage(chatId.toString(), "⚠ Нет активного запроса на отмену.");
        }

        Optional<String> cancellationDataOpt = userService.consumePendingCancellation(adminTelegramId);
        if (cancellationDataOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Данные об отмене не найдены.");
        }

        String[] parts = cancellationDataOpt.get().split(":");
        String regNumber = parts[0];
        CancellationType type = CancellationType.valueOf(parts[1]);

        return handleCloseWithoutExecution(chatId, regNumber, adminTelegramId, type, reason);
    }

    public SendMessage handleExcelUpload(Long chatId, Long telegramId, String fileName, String fileId, TelegramLongPollingBot bot) {
        try {
            User user = userService.findByTelegramId(telegramId).orElse(null);
            if (user == null) {
                return new SendMessage(chatId.toString(), "❌ Вы не зарегистрированы.");
            }

            if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
                return new SendMessage(chatId.toString(), "⚠ Формат не поддерживается. Загрузите .xlsx или .xls файл.");
            }

            GetFile getFile = new GetFile();
            getFile.setFileId(fileId);
            File tgFile = bot.execute(getFile);

            try (InputStream inputStream = bot.downloadFileAsStream(tgFile.getFilePath())) {
                var documents = documentParserService.parse(inputStream, user);
                auditLogService.logAction(
                        user,
                        "UPLOAD_EXCEL",
                        ActionCategory.DOCUMENT,
                        "Document",
                        null,
                        "Uploaded Excel file with " + documents.size() + " documents",
                        null,
                        null,
                        "Telegram Bot",
                        user.getTelegramUsername(),
                        true,
                        null,
                        Map.of("fileName", fileName, "documentCount", documents.size())
                        );
                return new SendMessage(chatId.toString(),
                        String.format("✅ Файл \"%s\" обработан. Загружено %d документов.", fileName, documents.size()));
            }

        } catch (Exception e) {
            log.error("Excel upload error: {}", e.getMessage(), e);
            return new SendMessage(chatId.toString(), "⚠ Ошибка при обработке файла. Попробуйте снова.");
        }
    }

    public SendMessage handleCloseWithoutExecution(Long chatId, String regNumber, Long adminTelegramId,
                                                   CancellationType type, String reason) {
        Optional<Document> documentOpt = documentService.findByRegNumber(regNumber);
        if (documentOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Документ с номером " + regNumber + " не найден.");
        }

        Optional<User> adminOpt = userService.findByTelegramId(adminTelegramId);
        if (adminOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Администратор не найден.");
        }

        Document document = documentOpt.get();
        User admin = adminOpt.get();
        User executor = document.getExecutor();

        try {
            DocumentStatus oldStatus = document.getStatus();
            document.setStatus(DocumentStatus.CANCELLED);
            documentService.save(document);

            documentCancellationService.createCancellation(
                    document,
                    admin,
                    reason,
                    type,
                    oldStatus
            );

            auditLogService.logAction(
                    admin,
                    "CANCEL_DOCUMENT",
                    ActionCategory.DOCUMENT,
                    "Document",
                    document.getId(),
                    "Document cancelled without execution",
                    Map.of("status", oldStatus.name()),
                    Map.of("status", DocumentStatus.CANCELLED.name(), "reason", reason),
                    "Telegram Bot",
                    admin.getTelegramUsername(),
                    true,
                    null,
                    null
            );

            notificationService.notifyUser(
                    executor.getTelegramId(),
                    String.format(
                            "❌ <b>Поручение закрыто без исполнения</b>\n\n" +
                                    "📄 Поручение №%s\n" +
                                    "📝 Описание: %s\n" +
                                    "💬 Причина: %s\n" +
                                    "👤 Закрыл: %s",
                            document.getRegNumber(),
                            document.getSummary(),
                            reason,
                            admin.getFullName()
                    ),
                    null
            );

            log.info("✅ Document {} closed without execution by admin {} (type={}, reason={})",
                    regNumber, adminTelegramId, type, reason);

            return new SendMessage(chatId.toString(),
                    "✅ Поручение №" + regNumber + " закрыто без исполнения.\n💬 Причина: " + reason);
        } catch (Exception e) {
            log.error("❌ Error closing document {} without execution: {}", regNumber, e.getMessage(), e);
            return new SendMessage(chatId.toString(), "⚠ Ошибка при закрытии поручения. Попробуйте позже.");
        }
    }

    public SendMessage handleTaskCompleted(Long chatId, String regNumber, Long adminTelegramId, String feedback) {
        Optional<Document> documentOpt = documentService.findByRegNumber(regNumber);
        if (documentOpt.isEmpty()) {
            SendMessage message = new SendMessage(chatId.toString(), "⚠ Документ с номером " + regNumber + " не найден.");
            message.enableHtml(true);
            return message;
        }

        Optional<User> adminOpt = userService.findByTelegramId(adminTelegramId);
        if (adminOpt.isEmpty()) {
            SendMessage message = new SendMessage(chatId.toString(), "⚠ Администратор не найден.");
            message.enableHtml(true);
            return message;
        }

        Document document = documentOpt.get();
        User admin = adminOpt.get();
        User executor = document.getExecutor();

        if (feedback == null) {
            userService.setWaitingForCompletionFeedback(adminTelegramId, regNumber);
            SendMessage message = new SendMessage(chatId.toString(),
                    String.format("🔍 <b>Подтверждение выполнения</b>\n\n" +
                                    "📄 Поручение №%s\n" +
                                    "📝 Описание: %s\n\n" +
                                    "Пожалуйста, введите комментарий или обратную связь для исполнителя.",
                            document.getRegNumber(), document.getSummary()));
            message.enableHtml(true);
            return message;
        } else {
            try {
                DocumentStatus oldStatus = document.getStatus();
                document.setStatus(DocumentStatus.COMPLETED);
                documentService.save(document);

                if (executor != null && executor.getDocuments() != null) {
                    executor.getDocuments().remove(document);
                    userService.save(executor);
                }

                ExecutionResult completionResult = ExecutionResult.builder()
                        .document(document)
                        .user(executor)
                        .resultText("Поручение отмечено как исполнено администратором.")
                        .submittedAt(LocalDateTime.now())
                        .reviewedBy(admin)
                        .reviewedAt(LocalDateTime.now())
                        .adminFeedback(feedback.trim())
                        .build();
                executionResultService.save(completionResult);

                auditLogService.logAction(
                        admin,
                        "MARK_COMPLETED",
                        ActionCategory.RESULT,
                        "Document",
                        document.getId(),
                        "Document marked as completed",
                        Map.of("status", oldStatus.name()),
                        Map.of("status", DocumentStatus.COMPLETED.name(), "feedback", feedback),
                        "Telegram Bot",
                        admin.getTelegramUsername(),
                        true,
                        null,
                        null
                );

                notificationService.notifyUser(
                        executor.getTelegramId(),
                        String.format(
                                "✅ <b>Поручение исполнено</b>\n\n" +
                                        "📄 Поручение №%s\n" +
                                        "📝 Описание: %s\n" +
                                        "👤 Подтвердил: %s\n" +
                                        "💬 Комментарий: %s",
                                document.getRegNumber(),
                                document.getSummary(),
                                admin.getFullName(),
                                feedback.trim()
                        ),
                        null
                );

                log.info("✅ Document {} marked as completed by admin {} with feedback: {}", regNumber, adminTelegramId, feedback);
                SendMessage message = new SendMessage(chatId.toString(), "✅ Поручение №" + regNumber + " отмечено как исполнено.");
                message.enableHtml(true);
                return message;
            } catch (Exception e) {
                log.error("❌ Error marking document {} as completed: {}", regNumber, e.getMessage(), e);
                SendMessage message = new SendMessage(chatId.toString(), "⚠ Ошибка при подтверждении исполнения. Попробуйте позже.");
                message.enableHtml(true);
                return message;
            }
        }
    }

    public SendMessage handleSendForRevision(Long chatId, String regNumber, Long adminTelegramId) {
        Optional<Document> documentOpt = documentService.findByRegNumber(regNumber);
        if (documentOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Документ с номером " + regNumber + " не найден.");
        }

        Optional<User> adminOpt = userService.findByTelegramId(adminTelegramId);
        if (adminOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Администратор не найден.");
        }

        Document document = documentOpt.get();
        userService.setWaitingForRevisionReason(adminTelegramId, regNumber);

        String message = String.format(
                "🔄 <b>Отправка поручения на доработку</b>\n\n" +
                        "📄 Поручение №%s\n" +
                        "📝 Описание: %s\n\n" +
                        "Пожалуйста, укажите причину отправки на доработку.",
                document.getRegNumber(),
                document.getSummary()
        );

        SendMessage sendMessage = new SendMessage(chatId.toString(), message);
        sendMessage.enableHtml(true);
        return sendMessage;
    }

    public SendMessage handleRevisionReasonInput(Long chatId, Long adminTelegramId, String reason) {
        if (!userService.isWaitingForRevisionReason(adminTelegramId)) {
            return new SendMessage(chatId.toString(), "⚠ Нет активного запроса на доработку.");
        }

        Optional<String> regNumberOpt = userService.consumeWaitingForRevisionReason(adminTelegramId);
        if (regNumberOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Данные о документе не найдены.");
        }

        String regNumber = regNumberOpt.get();
        Optional<Document> documentOpt = documentService.findByRegNumber(regNumber);
        if (documentOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Документ с номером " + regNumber + " не найден.");
        }

        Optional<User> adminOpt = userService.findByTelegramId(adminTelegramId);
        if (adminOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Администратор не найден.");
        }

        Document document = documentOpt.get();
        User admin = adminOpt.get();
        User executor = document.getExecutor();

        try {
            DocumentStatus oldStatus = document.getStatus();
            document.setStatus(DocumentStatus.REVISION);
            documentService.save(document);

            auditLogService.logAction(
                    admin,
                    "SEND_FOR_REVISION",
                    ActionCategory.RESULT,
                    "Document",
                    document.getId(),
                    "Document sent for revision",
                    Map.of("status", oldStatus.name()),
                    Map.of("status", DocumentStatus.REVISION.name(), "reason", reason),
                    "Telegram Bot",
                    admin.getTelegramUsername(),
                    true,
                    null,
                    null
            );

            notificationService.notifyUser(
                    executor.getTelegramId(),
                    String.format(
                            "🔄 <b>Поручение отправлено на доработку</b>\n\n" +
                                    "📄 Поручение №%s\n" +
                                    "📝 Описание: %s\n" +
                                    "💬 Причина доработки: %s\n" +
                                    "👤 Отправил на доработку: %s\n\n" +
                                    "Пожалуйста, доработайте и отправьте файл заново.",
                            document.getRegNumber(),
                            document.getSummary(),
                            reason,
                            admin.getFullName()
                    ),
                    null
            );

            log.info("✅ Document {} sent for revision by admin {} with reason: {}", regNumber, adminTelegramId, reason);

            return new SendMessage(chatId.toString(), "✅ Поручение №" + regNumber + " отправлено на доработку с указанием причины.");
        } catch (Exception e) {
            log.error("❌ Error sending document {} for revision: {}", regNumber, e.getMessage(), e);
            return new SendMessage(chatId.toString(), "⚠ Ошибка при отправке на доработку. Попробуйте позже.");
        }
    }

    public SendMessage handleTasksCommand(Long chatId, Long adminTelegramId) {
        Optional<User> adminOpt = userService.findByTelegramId(adminTelegramId);
        if (adminOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Администратор не найден.");
        }

        User admin = adminOpt.get();
        List<Document> documents = documentService.findByUploadedBy(admin);

        if (documents.isEmpty()) {
            return new SendMessage(chatId.toString(), "📭 Вы еще не загружали поручений.");
        }

        Map<DocumentStatus, List<Document>> groupedByStatus = documents.stream()
                .collect(Collectors.groupingBy(Document::getStatus));

        StringBuilder text = new StringBuilder();
        text.append("📋 <b>Ваши поручения</b>\n");
        text.append("Всего загружено: ").append(documents.size()).append(" поручений\n\n");

        text.append("📊 <b>Статистика:</b>\n");
        for (DocumentStatus status : DocumentStatus.values()) {
            int count = groupedByStatus.getOrDefault(status, List.of()).size();
            if (count > 0) {
                text.append("  ").append(status.getDisplayName()).append(": ").append(count).append("\n");
            }
        }
        text.append("\n");

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(
                InlineKeyboardButton.builder()
                        .text("🆕 В работе (" + groupedByStatus.getOrDefault(DocumentStatus.IN_PROGRESS, List.of()).size() + ")")
                        .callbackData("tasks_filter:IN_PROGRESS")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("⏳ На рассмотрении (" + groupedByStatus.getOrDefault(DocumentStatus.PENDING, List.of()).size() + ")")
                        .callbackData("tasks_filter:PENDING")
                        .build()
        ));

        keyboard.add(List.of(
                InlineKeyboardButton.builder()
                        .text("🔄 На доработке (" + groupedByStatus.getOrDefault(DocumentStatus.REVISION, List.of()).size() + ")")
                        .callbackData("tasks_filter:REVISION")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("✅ Исполнено (" + groupedByStatus.getOrDefault(DocumentStatus.COMPLETED, List.of()).size() + ")")
                        .callbackData("tasks_filter:COMPLETED")
                        .build()
        ));

        keyboard.add(List.of(
                InlineKeyboardButton.builder()
                        .text("❌ Отменено (" + groupedByStatus.getOrDefault(DocumentStatus.CANCELLED, List.of()).size() + ")")
                        .callbackData("tasks_filter:CANCELLED")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("📋 Все")
                        .callbackData("tasks_filter:ALL")
                        .build()
        ));

        text.append("💡 <i>Выберите статус для просмотра деталей</i>");

        SendMessage message = new SendMessage(chatId.toString(), text.toString());
        message.enableHtml(true);
        message.setReplyMarkup(new InlineKeyboardMarkup(keyboard));
        return message;
    }

    public SendMessage handleTasksFilter(Long chatId, Long adminTelegramId, String statusFilter) {
        Optional<User> adminOpt = userService.findByTelegramId(adminTelegramId);
        if (adminOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Администратор не найден.");
        }

        User admin = adminOpt.get();
        List<Document> documents;

        if ("ALL".equals(statusFilter)) {
            documents = documentService.findByUploadedBy(admin);
        } else {
            DocumentStatus status = DocumentStatus.valueOf(statusFilter);
            documents = documentService.findByUploadedByAndStatus(admin, status);
        }

        if (documents.isEmpty()) {
            String statusName = "ALL".equals(statusFilter) ? "всего" : DocumentStatus.valueOf(statusFilter).getDisplayName();
            return new SendMessage(chatId.toString(),
                    "📭 Нет поручений со статусом: " + statusName);
        }

        StringBuilder text = new StringBuilder();
        String filterName = "ALL".equals(statusFilter) ? "Все поручения" : DocumentStatus.valueOf(statusFilter).getDisplayName();
        text.append("📋 <b>").append(filterName).append("</b>\n");
        text.append("Найдено: ").append(documents.size()).append(" поручений\n\n");

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        int counter = 1;
        for (Document doc : documents) {
            text.append(counter++).append(". 📄 <b>").append(doc.getRegNumber()).append("</b>\n");
            text.append("   👤 Исполнитель: ").append(doc.getExecutor().getFullName()).append("\n");
            text.append("   📝 ").append(doc.getSummary()).append("\n");
            text.append("   🗓 Срок: ").append(doc.getExecutionDate() != null ? doc.getExecutionDate() : "не указан").append("\n");
            text.append("   📊 Статус: ").append(doc.getStatus().getDisplayName()).append("\n");

            List<InlineKeyboardButton> buttons = new ArrayList<>();
            if (doc.getStatus() == DocumentStatus.PENDING) {
                buttons.add(InlineKeyboardButton.builder()
                        .text("🔍 Рассмотреть")
                        .callbackData("review:" + doc.getRegNumber())
                        .build());
            }
            if (doc.getStatus() != DocumentStatus.CANCELLED && doc.getStatus() != DocumentStatus.COMPLETED) {
                buttons.add(InlineKeyboardButton.builder()
                        .text("🚫 Отменить")
                        .callbackData("close_without_execution:" + doc.getRegNumber())
                        .build());
            }
            if (doc.getStatus() == DocumentStatus.IN_PROGRESS || doc.getStatus() == DocumentStatus.PENDING) {
                buttons.add(InlineKeyboardButton.builder()
                        .text("✅ Завершить")
                        .callbackData("task_completed:" + doc.getRegNumber())
                        .build());
            }
            if (doc.getStatus() == DocumentStatus.IN_PROGRESS || doc.getStatus() == DocumentStatus.PENDING) {
                buttons.add(InlineKeyboardButton.builder()
                        .text("🔄 На доработку")
                        .callbackData("send_for_revision:" + doc.getRegNumber())
                        .build());
            }
            if (!buttons.isEmpty()) {
                keyboard.add(buttons);
            }

            text.append("\n");
        }

        keyboard.add(List.of(
                InlineKeyboardButton.builder()
                        .text("⬅ Назад к статистике")
                        .callbackData("tasks_back")
                        .build()
        ));

        SendMessage message = new SendMessage(chatId.toString(), text.toString());
        message.enableHtml(true);
        message.setReplyMarkup(new InlineKeyboardMarkup(keyboard));
        return message;
    }

    public SendMessage handleApproveExtendRequest(Long chatId, Long requestId, Long adminTelegramId) {
        Optional<ExtensionRequest> requestOpt = extensionRequestService.findById(requestId);
        if (requestOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Запрос не найден.");
        }

        ExtensionRequest request = requestOpt.get();

        userService.setWaitingForNewDate(adminTelegramId, requestId);

        String message = String.format(
                "🗓 <b>Одобрение запроса на продление</b>\n\n" +
                        "📄 Поручение №%s\n" +
                        "👤 Исполнитель: %s\n" +
                        "📅 Текущий срок: %s\n" +
                        "💬 Причина: %s\n\n" +
                        "Введите новую дату исполнения в формате <code>ГГГГ-ММ-ДД</code>\n" +
                        "Например: <code>2025-12-31</code>",
                request.getDocument().getRegNumber(),
                request.getUser().getFullName(),
                request.getDocument().getExecutionDate(),
                request.getReason() != null ? request.getReason() : "—"
        );

        SendMessage sendMessage = new SendMessage(chatId.toString(), message);
        sendMessage.enableHtml(true);
        return sendMessage;
    }

    public SendMessage handleRejectExtendRequest(Long chatId, Long requestId, Long adminTelegramId) {
        Optional<ExtensionRequest> reqOpt = extensionRequestService.findById(requestId);
        if (reqOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Запрос не найден.");
        }

        ExtensionRequest request = reqOpt.get();
        Optional<User> adminOpt = userService.findByTelegramId(adminTelegramId);

        if (adminOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Администратор не найден.");
        }

        User admin = adminOpt.get();

        try {
            auditLogService.logAction(
                    admin,
                    "REJECT_EXTENSION",
                    ActionCategory.EXTENSION,
                    "ExtensionRequest",
                    request.getId(),
                    "Extension request rejected",
                    null,
                    null,
                    "Telegram Bot",
                    admin.getTelegramUsername(),
                    true,
                    null,
                    Map.of("documentId", request.getDocument().getId(), "reason", "Request rejected by admin")
                    );

            extensionRequestService.rejectRequest(
                    request,
                    admin,
                    "Запрос отклонён администратором"
            );

            notificationService.notifyUser(
                    request.getUser().getTelegramId(),
                    String.format(
                            "❌ <b>Запрос отклонён</b>\n\n" +
                                    "📄 Поручение №%s\n" +
                                    "📝 Описание: %s\n" +
                                    "🗓 Срок исполнения: %s\n" +
                                    "👤 Отклонил: %s\n\n" +
                                    "Пожалуйста, выполните поручение в установленный срок.",
                            request.getDocument().getRegNumber(),
                            request.getDocument().getSummary(),
                            request.getDocument().getExecutionDate(),
                            admin.getFullName()
                    ),
                    null
            );

            log.info("❌ Extension request rejected: doc={}, admin={}",
                    request.getDocument().getRegNumber(), admin.getTelegramId());

            return new SendMessage(chatId.toString(),
                    String.format("🚫 Запрос на продление поручения №%s отклонён.",
                            request.getDocument().getRegNumber()));

        } catch (Exception e) {
            log.error("Error rejecting extension request: {}", e.getMessage(), e);
            return new SendMessage(chatId.toString(),
                    "⚠ Произошла ошибка при отклонении запроса. Попробуйте позже.");
        }
    }

    public SendMessage handleNewExecutionDate(Long chatId, Long adminTelegramId, String text) {
        if (!userService.isWaitingForNewDate(adminTelegramId)) {
            return new SendMessage(chatId.toString(), "⚠ Нет активного запроса на продление. Используйте /pending для просмотра запросов.");
        }

        Optional<Long> requestIdOpt = userService.consumeWaitingForNewDate(adminTelegramId);
        if (requestIdOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Запрос не найден.");
        }

        Long requestId = requestIdOpt.get();
        Optional<ExtensionRequest> reqOpt = extensionRequestService.findById(requestId);

        if (reqOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Запрос не найден.");
        }

        ExtensionRequest request = reqOpt.get();
        Optional<User> adminOpt = userService.findByTelegramId(adminTelegramId);

        if (adminOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Администратор не найден.");
        }

        User admin = adminOpt.get();

        try {
            LocalDate newDate = LocalDate.parse(text.trim());
            LocalDate currentDate = request.getDocument().getExecutionDate();

            if (newDate.isBefore(currentDate) || newDate.isEqual(currentDate)) {
                userService.setWaitingForNewDate(adminTelegramId, requestId);
                return new SendMessage(chatId.toString(),
                        String.format("⚠ Новая дата (%s) должна быть позже текущей (%s). Попробуйте снова.",
                                newDate, currentDate));
            }

            Document doc = request.getDocument();
            LocalDate oldDate = doc.getExecutionDate();
            doc.setExecutionDate(newDate);
            documentService.save(doc);

            auditLogService.logAction(
                    admin,
                    "APPROVE_EXTENSION",
                    ActionCategory.EXTENSION,
                    "ExtensionRequest",
                    request.getId(),
                    "Extension request approved",
                    Map.of("oldDate", oldDate.toString()),
                    Map.of("newDate", newDate.toString(), "reason", request.getReason()),
                    "Telegram Bot",
                    admin.getTelegramUsername(),
                    true,
                    null,
                    null
            );

            extensionRequestService.approveRequest(
                    request,
                    admin,
                    String.format("Срок продлён с %s до %s", oldDate, newDate)
            );

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(
                    List.of(
                            InlineKeyboardButton.builder()
                                    .text("✅ Одобрить")
                                    .callbackData("approve_extend:" + request.getId())
                                    .build(),
                            InlineKeyboardButton.builder()
                                    .text("❌ Отклонить")
                                    .callbackData("reject_extend:" + request.getId())
                                    .build()
                    )
            ));
            User adminUploader = doc.getUploadedBy();
            if (adminUploader != null && adminUploader.getTelegramId() != null) {
                notificationService.notifyAdmin(
                        adminUploader.getTelegramId(),
                        String.format(
                                "🔔 <b>Поручение продлено</b>\n\n" +
                                        "📄 Поручение №%s\n" +
                                        "👤 Исполнитель: %s\n" +
                                        "🗓 Старый срок: %s\n" +
                                        "🗓 Новый срок: %s\n" +
                                        "👤 Одобрил: %s\n" +
                                        "💬 Причина: %s",
                                doc.getRegNumber(),
                                request.getUser().getFullName(),
                                oldDate,
                                newDate,
                                admin.getFullName(),
                                request.getReason() != null ? request.getReason() : "—"
                        ),
                        keyboard
                );
                log.info("🔔 Notification sent to admin {} about extension of document {}",
                        adminUploader.getTelegramId(), doc.getRegNumber());
            } else {
                log.warn("No admin uploader found for document {}", doc.getRegNumber());
            }

            log.info("✅ Extension request approved: doc={}, newDate={}, admin={}",
                    doc.getRegNumber(), newDate, admin.getTelegramId());

            return new SendMessage(chatId.toString(),
                    String.format("✅ Срок поручения №%s успешно продлён до %s", doc.getRegNumber(), newDate));

        } catch (DateTimeParseException e) {
            userService.setWaitingForNewDate(adminTelegramId, requestId);
            return new SendMessage(chatId.toString(),
                    "⚠ Неверный формат даты. Используйте формат ГГГГ-ММ-ДД (например, 2025-12-31).");
        } catch (Exception e) {
            log.error("Error approving extension request: {}", e.getMessage(), e);
            return new SendMessage(chatId.toString(),
                    "⚠ Произошла ошибка при одобрении запроса. Попробуйте позже.");
        }
    }
}