package org.info.tg_bot.bot.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.ExtensionRequest;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.DepartmentType;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.info.tg_bot.model.enums.PositionType;
import org.info.tg_bot.service.*;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCommandHandler {

    private final UserService userService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    private final ExtensionRequestService extensionRequestService;
    private final FileForwardingService fileForwardingService;


    private static final int DEPARTMENTS_PER_PAGE = 5;
    private static final int POSITIONS_PER_PAGE = 5;

    private final Map<Long, Long> waitingForExtensionReason = new HashMap<>();

    // ---------- /start ----------
    public SendMessage handleStartCommand(Long chatId, Long telegramId, String username) {
        if (userService.existsByTelegramId(telegramId)) {
            User user = userService.findByTelegramId(telegramId).orElseThrow();
            return new SendMessage(chatId.toString(),
                    String.format("С возвращением, %s!", user.getName()));
        }

        userService.startRegistration(telegramId, username);
        return createDepartmentInlineKeyboard(chatId, 0);
    }

    // ---------- Регистрация ----------
    public SendMessage handleRegistrationStep(Long chatId, Long telegramId, String data) {
        if (userService.existsByTelegramId(telegramId)) {
            return new SendMessage(chatId.toString(), "Вы уже зарегистрированы.");
        }

        String currentStep = userService.getRegistrationStep(telegramId);
        if (currentStep.isEmpty()) {
            return new SendMessage(chatId.toString(), "Ошибка регистрации. Используйте /start для начала.");
        }

        data = data == null ? "" : data.strip();

        switch (currentStep) {
            case "DEPARTMENT" -> {
                if (data.startsWith("dept:")) {
                    int index = Integer.parseInt(data.substring(5));
                    DepartmentType department = DepartmentType.values()[index];
                    userService.updateRegistrationDepartment(telegramId, department.getDisplayName());
                    return createPositionInlineKeyboard(chatId, 0);
                } else if (data.startsWith("page:")) {
                    int page = Integer.parseInt(data.substring(5));
                    return createDepartmentInlineKeyboard(chatId, page);
                } else {
                    DepartmentType department = DepartmentType.fromDisplayName(data);
                    userService.updateRegistrationDepartment(telegramId,
                            department != null ? department.getDisplayName() : data);
                    return createPositionInlineKeyboard(chatId, 0);
                }
            }
            case "POSITION" -> {
                if (data.startsWith("pos:")) {
                    int index = Integer.parseInt(data.substring(4));
                    PositionType position = PositionType.values()[index];
                    userService.updateRegistrationPosition(telegramId, position.getDisplayName());
                    return new SendMessage(chatId.toString(), "Пожалуйста, введите ваше имя:");
                } else if (data.startsWith("pospage:")) {
                    int page = Integer.parseInt(data.substring(8));
                    return createPositionInlineKeyboard(chatId, page);
                } else {
                    PositionType position = PositionType.fromDisplayName(data);
                    userService.updateRegistrationPosition(telegramId,
                            position != null ? position.getDisplayName() : data);
                    return new SendMessage(chatId.toString(), "Пожалуйста, введите ваше имя:");
                }
            }
            case "NAME" -> {
                userService.updateRegistrationName(telegramId, data);
                return new SendMessage(chatId.toString(), "Пожалуйста, введите вашу фамилию:");
            }
            case "SURNAME" -> {
                userService.updateRegistrationSurname(telegramId, data);
                return new SendMessage(chatId.toString(), "Пожалуйста, введите ваше отчество:");
            }
            case "FATHERNAME" -> {
                userService.completeRegistration(telegramId, data);
                User user = userService.findByTelegramId(telegramId).orElseThrow();
                return new SendMessage(chatId.toString(),
                        String.format("✅ Регистрация завершена! Добро пожаловать, %s!", user.getFullName()));
            }
            default -> {
                return new SendMessage(chatId.toString(), "Ошибка регистрации. Используйте /start для начала.");
            }
        }
    }

    // ---------- Выбор отдела / должности ----------
    private SendMessage createDepartmentInlineKeyboard(Long chatId, int page) {
        DepartmentType[] departments = DepartmentType.values();
        int start = page * DEPARTMENTS_PER_PAGE;
        int end = Math.min(start + DEPARTMENTS_PER_PAGE, departments.length);

        SendMessage message = new SendMessage(chatId.toString(),
                "Пожалуйста, выберите ваш отдел из списка или напишите его вручную:");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = start; i < end; i++) {
            DepartmentType dept = departments[i];
            rows.add(List.of(createButton(dept.getDisplayName(), "dept:" + i)));
        }

        addPagination(rows, page, departments.length, "page:");
        message.setReplyMarkup(new InlineKeyboardMarkup(rows));
        return message;
    }

    private SendMessage createPositionInlineKeyboard(Long chatId, int page) {
        PositionType[] positions = PositionType.values();
        int start = page * POSITIONS_PER_PAGE;
        int end = Math.min(start + POSITIONS_PER_PAGE, positions.length);

        SendMessage message = new SendMessage(chatId.toString(),
                "Пожалуйста, выберите вашу должность из списка или введите вручную:");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = start; i < end; i++) {
            PositionType pos = positions[i];
            rows.add(List.of(createButton(pos.getDisplayName(), "pos:" + i)));
        }

        addPagination(rows, page, positions.length, "pospage:");
        message.setReplyMarkup(new InlineKeyboardMarkup(rows));
        return message;
    }

    private void addPagination(List<List<InlineKeyboardButton>> rows, int page, int total, String prefix) {
        List<InlineKeyboardButton> pagination = new ArrayList<>();
        if (page > 0) pagination.add(createButton("⬅ Назад", prefix + (page - 1)));
        if ((page + 1) * 5 < total) pagination.add(createButton("Вперед ➡", prefix + (page + 1)));
        if (!pagination.isEmpty()) rows.add(pagination);
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    // ---------- Команда /mytasks ----------
    public SendMessage handleMyTasksCommand(Long chatId, Long telegramId) {
        Optional<User> optionalUser = userService.findByTelegramId(telegramId);
        if (optionalUser.isEmpty()) {
            return new SendMessage(chatId.toString(), "❌ Вы не зарегистрированы. Используйте /start.");
        }

        User user = optionalUser.get();

        List<Document> activeDocuments = documentService.findActiveDocumentsByExecutor(user);

        if (activeDocuments.isEmpty()) {
            return new SendMessage(chatId.toString(), "📭 У вас пока нет активных поручений.");
        }

        StringBuilder sb = new StringBuilder("📋 *Ваши поручения:*\n\n");
        int counter = 1;
        for (Document doc : activeDocuments) {
            sb.append(String.format("%d️⃣  Номер поручения: %s %s — срок: %s, статус: %s\n",
                    counter++,
                    doc.getRegNumber(),
                    doc.getSummary(),
                    doc.getExecutionDate() != null ? doc.getExecutionDate() : "не указан",
                    doc.getStatus().getDisplayName()));

        }
        sb.append("\nВведите номер поручения, чтобы просмотреть детали.");

        userService.setWaitingForTaskNumber(telegramId, true);

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");
        return message;
    }

    // ---------- Обработка callback-кнопок ----------
    public SendMessage handleCallback(Long chatId, Long telegramId, String callbackData) {
        if (userService.isWaitingForTaskNumber(telegramId)) {
            userService.clearWaitingForTaskNumber(telegramId);
        }
        // --- Отправка результата ---
        if (callbackData.startsWith("send_result:")) {
            String regNumber = callbackData.substring("send_result:".length());
            Optional<Document> optionalDoc = documentService.findByRegNumber(regNumber);

            if (optionalDoc.isEmpty()) {
                return new SendMessage(chatId.toString(), "⚠ Документ с таким номером не найден.");
            }

            Document doc = optionalDoc.get();
            userService.setWaitingForFile(telegramId, doc.getId());

            return new SendMessage(chatId.toString(),
                    "📤 Пожалуйста, отправьте файл для поручения №" + regNumber + ".");
        }

        // --- Запрос на продление ---
        if (callbackData.startsWith("request_extend:")) {
            String regNumber = callbackData.substring("request_extend:".length());
            Optional<Document> optionalDoc = documentService.findByRegNumber(regNumber);
            if (optionalDoc.isEmpty()) {
                return new SendMessage(chatId.toString(), "⚠ Документ с таким номером не найден.");
            }

            Document doc = optionalDoc.get();

            waitingForExtensionReason.put(telegramId, doc.getId());

            return new SendMessage(chatId.toString(),
                    "💬 Пожалуйста, укажите причину запроса на продление поручения №" + regNumber + ":");
        }

        return new SendMessage(chatId.toString(), "Неизвестное действие.");
    }

    // ---------- Обработка причины продления ----------
    public SendMessage handleExtensionReasonInput(Long chatId, Long telegramId, String reason) {
        if (!waitingForExtensionReason.containsKey(telegramId)) {
            return new SendMessage(chatId.toString(), "⚠ Нет активного запроса на продление.");
        }

        Long documentId = waitingForExtensionReason.remove(telegramId);
        Optional<Document> optionalDoc = documentService.findById(documentId);

        if (optionalDoc.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Документ не найден.");
        }

        Document doc = optionalDoc.get();
        Optional<User> optionalUser = userService.findByTelegramId(telegramId);

        if (optionalUser.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Пользователь не найден.");
        }

        User user = optionalUser.get();
        User admin = doc.getUploadedBy();

        if (admin == null || admin.getTelegramId() == null) {
            return new SendMessage(chatId.toString(), "⚠ Не удалось определить администратора для отправки запроса.");
        }

        try {
            ExtensionRequest request = extensionRequestService.createRequest(
                    doc,
                    user,
                    LocalDateTime.now(),
                    reason
            );

            // Отправляем уведомление администратору с кнопками
            String notificationText = String.format(
                    "📩 <b>Запрос на продление</b>\n\n" +
                            "👤 Исполнитель: %s\n" +
                            "📄 Поручение №%s\n" +
                            "📝 Описание: %s\n" +
                            "🗓 Текущий срок: %s\n" +
                            "💬 Причина: %s",
                    user.getFullName(),
                    doc.getRegNumber(),
                    doc.getSummary(),
                    doc.getExecutionDate(),
                    reason
            );

            // Создаем inline кнопки для одобрения/отклонения
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

            notificationService.notifyAdmin(admin.getTelegramId(), notificationText, keyboard);

            return new SendMessage(chatId.toString(),
                    "✅ Запрос на продление отправлен администратору. Ожидайте ответа.");

        } catch (IllegalStateException e) {
            log.warn("Extension request already exists: {}", e.getMessage());
            return new SendMessage(chatId.toString(), e.getMessage());
        } catch (Exception e) {
            log.error("Error creating extension request: {}", e.getMessage(), e);
            return new SendMessage(chatId.toString(), "⚠ Ошибка при создании запроса. Попробуйте позже.");
        }
    }

    // ---------- Выбор поручения по номеру ----------
    public SendMessage handleTaskSelection(Long chatId, Long telegramId, String input) {
        Optional<User> optionalUser = userService.findByTelegramId(telegramId);
        if (optionalUser.isEmpty()) {
            return new SendMessage(chatId.toString(), "❌ Вы не зарегистрированы. Используйте /start.");
        }

        User user = optionalUser.get();
        String regNumber = input.strip();

        Optional<Document> optionalDoc = documentService.findByRegNumberAndExecutor(regNumber, user);
        if (optionalDoc.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Поручение с таким регистрационным номером не найдено.");
        }

        userService.clearWaitingForTaskNumber(telegramId);

        Document doc = optionalDoc.get();
        String text = String.format(
                "📄 *%s*\nРег. №: %s\nПодразделение: %s\nСрок исполнения: %s",
                doc.getSummary(),
                doc.getRegNumber(),
                doc.getDepartment(),
                doc.getExecutionDate() != null ? doc.getExecutionDate() : "не указан"
        );

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(
                List.of(
                        InlineKeyboardButton.builder().text("📤 Отправить результат")
                                .callbackData("send_result:" + doc.getRegNumber()).build(),
                        InlineKeyboardButton.builder().text("⏳ Попросить продлить")
                                .callbackData("request_extend:" + doc.getRegNumber()).build()
                )
        ));

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();
    }

    public SendMessage handleFileUpload(Long chatId, Long telegramId, org.telegram.telegrambots.meta.api.objects.Document file, TelegramLongPollingBot bot) {
        try {
            Long documentId = userService.getDocumentIdWaitingForFile(telegramId);
            if (documentId == null) {
                return new SendMessage(chatId.toString(), "⚠ Не найдено поручение для отправки файла. Попробуйте снова.");
            }

            Optional<Document> optionalDoc = documentService.findById(documentId);
            if (optionalDoc.isEmpty()) {
                userService.clearWaitingForFile(telegramId);
                return new SendMessage(chatId.toString(), "⚠ Поручение не найдено. Попробуйте снова.");
            }

            Document doc = optionalDoc.get();
            User admin = doc.getUploadedBy();

            if (admin == null || admin.getTelegramId() == null) {
                userService.clearWaitingForFile(telegramId);
                return new SendMessage(chatId.toString(), "⚠ Администратор не найден. Файл не был отправлен.");
            }

            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                userService.clearWaitingForFile(telegramId);
                return new SendMessage(chatId.toString(), "⚠ Пользователь не найден.");
            }

            User executor = userOpt.get();

            String caption = String.format(
                    "📬 <b>Результат выполнения поручения</b>\n\n" +
                            "👤 Исполнитель: %s\n" +
                            "📄 Поручение №%s\n" +
                            "📝 Описание: %s\n" +
                            "🗓 Срок исполнения: %s\n" +
                            "📎 Файл: %s",
                    executor.getFullName(),
                    doc.getRegNumber(),
                    doc.getSummary(),
                    doc.getExecutionDate(),
                    file.getFileName()
            );

            fileForwardingService.sendDocumentToAdmin(
                    admin.getTelegramId(),
                    caption,
                    file.getFileId(),
                    file.getFileName(),
                    doc.getRegNumber()
            );

            userService.clearWaitingForFile(telegramId);
            doc.setStatus(DocumentStatus.PENDING);
            documentService.save(doc);
            log.info("✅ File sent from user {} to admin {} for document {}",
                    telegramId, admin.getTelegramId(), doc.getRegNumber());

            return new SendMessage(chatId.toString(), "✅ Файл успешно отправлен администратору на рассмотрение.");

        } catch (Exception e) {
            log.error("❌ Ошибка при пересылке файла: {}", e.getMessage(), e);
            return new SendMessage(chatId.toString(), "⚠ Ошибка при отправке файла. Попробуйте позже.");
        }
    }

    // ---------- Проверка состояний ----------
    public boolean isWaitingForExtensionReason(Long telegramId) {
        return waitingForExtensionReason.containsKey(telegramId);
    }

    // ---------- /help ----------
    public SendMessage handleHelpCommand(Long chatId) {
        String helpText = """
                Доступные команды:
                /start - Зарегистрироваться или начать работу
                /mytasks - Посмотреть ваши задачи
                /help - Показать это сообщение
                """;
        return new SendMessage(chatId.toString(), helpText);
    }
}