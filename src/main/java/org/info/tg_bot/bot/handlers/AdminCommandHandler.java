package org.info.tg_bot.bot.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.ExtensionRequest;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.service.*;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminCommandHandler {

    private final UserService userService;
    private final DocumentService documentService;
    private final DocumentParserService documentParserService;
    private final ExtensionRequestService extensionRequestService;
    private final NotificationService notificationService;

    private final Map<Long, Long> waitingForNewDate = new HashMap<>();

    // --- Commands ---

    public SendMessage handleStartCommand(Long chatId, String username) {
        String msg = String.format(
                "👋 Здравствуйте, %s!\n\n" +
                        "Вы авторизованы как администратор. " +
                        "Используйте /upload для загрузки Excel-файлов или /pending для просмотра запросов на продление.",
                username != null ? username : "администратор"
        );
        return new SendMessage(chatId.toString(), msg);
    }

    public SendMessage handleHelpCommand(Long chatId) {
        String help = """
                📘 Доступные команды:
                /start – начать работу
                /upload – загрузить Excel с поручениями
                /pending – просмотреть запросы на продление
                /help – показать это сообщение
                """;
        return new SendMessage(chatId.toString(), help);
    }

    public SendMessage handleUploadCommand(Long chatId) {
        return new SendMessage(chatId.toString(), "📤 Отправьте Excel-файл (.xlsx или .xls).");
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
                return new SendMessage(chatId.toString(),
                        String.format("✅ Файл \"%s\" обработан. Загружено %d документов.", fileName, documents.size()));
            }

        } catch (Exception e) {
            log.error("Excel upload error: {}", e.getMessage(), e);
            return new SendMessage(chatId.toString(), "⚠ Ошибка при обработке файла. Попробуйте снова.");
        }
    }

    public SendMessage handlePendingRequests(Long chatId) {
        List<ExtensionRequest> pending = extensionRequestService.findPendingRequests();

        if (pending.isEmpty()) {
            return new SendMessage(chatId.toString(), "📭 Нет ожидающих запросов на продление.");
        }

        StringBuilder text = new StringBuilder("📋 <b>Запросы на продление:</b>\n\n");

        int counter = 1;
        for (ExtensionRequest req : pending) {
            text.append(counter++).append(". 📄 Поручение №<b>").append(req.getDocument().getRegNumber()).append("</b>\n")
                    .append("   👤 Исполнитель: ").append(req.getUser().getFullName()).append("\n")
                    .append("   🗓 Текущий срок: ").append(req.getDocument().getExecutionDate()).append("\n")
                    .append("   💬 Причина: ").append(req.getReason() != null ? req.getReason() : "—").append("\n")
                    .append("   📅 Запрошено: ").append(req.getCreatedAt().toLocalDate()).append("\n\n");
        }

        text.append("\n💡 <i>Нажмите на кнопки ниже для одобрения или отклонения запросов</i>");

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (ExtensionRequest req : pending) {
            keyboard.add(List.of(
                    InlineKeyboardButton.builder()
                            .text("✅ Одобрить №" + req.getDocument().getRegNumber())
                            .callbackData("approve_extend:" + req.getId())
                            .build(),
                    InlineKeyboardButton.builder()
                            .text("❌ Отклонить №" + req.getDocument().getRegNumber())
                            .callbackData("reject_extend:" + req.getId())
                            .build()
            ));
        }

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

        waitingForNewDate.put(adminTelegramId, requestId);

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

    public SendMessage handleNewExecutionDate(Long chatId, Long adminTelegramId, String text) {
        if (!waitingForNewDate.containsKey(adminTelegramId)) {
            return new SendMessage(chatId.toString(), "⚠ Нет активного запроса на продление. Используйте /pending для просмотра запросов.");
        }

        Long requestId = waitingForNewDate.remove(adminTelegramId);
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
                waitingForNewDate.put(adminTelegramId, requestId);
                return new SendMessage(chatId.toString(),
                        String.format("⚠ Новая дата (%s) должна быть позже текущей (%s). Попробуйте снова.",
                                newDate, currentDate));
            }

            Document doc = request.getDocument();
            LocalDate oldDate = doc.getExecutionDate();
            doc.setExecutionDate(newDate);
            documentService.save(doc);

            extensionRequestService.approveRequest(
                    request,
                    admin,
                    String.format("Срок продлён с %s до %s", oldDate, newDate)
            );

            notificationService.notifyUser(
                    request.getUser().getTelegramId(),
                    String.format(
                            "✅ <b>Запрос одобрен!</b>\n\n" +
                                    "📄 Поручение №%s\n" +
                                    "🗓 Старый срок: %s\n" +
                                    "🗓 Новый срок: %s\n" +
                                    "👤 Одобрил: %s",
                            doc.getRegNumber(),
                            oldDate,
                            newDate,
                            admin.getFullName()
                    )
            );

            log.info("✅ Extension request approved: doc={}, newDate={}, admin={}",
                    doc.getRegNumber(), newDate, admin.getTelegramId());

            return new SendMessage(chatId.toString(),
                    String.format("✅ Срок поручения №%s успешно продлён до %s", doc.getRegNumber(), newDate));

        } catch (DateTimeParseException e) {
            waitingForNewDate.put(adminTelegramId, requestId);
            return new SendMessage(chatId.toString(),
                    "⚠ Неверный формат даты. Используйте формат ГГГГ-ММ-ДД (например, 2025-12-31).");
        } catch (Exception e) {
            log.error("Error approving extension request: {}", e.getMessage(), e);
            return new SendMessage(chatId.toString(),
                    "⚠ Произошла ошибка при одобрении запроса. Попробуйте позже.");
        }
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
                    )
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

    public boolean isWaitingForNewDate(Long adminTelegramId) {
        return waitingForNewDate.containsKey(adminTelegramId);
    }
}