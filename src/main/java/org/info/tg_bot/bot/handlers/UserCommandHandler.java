package org.info.tg_bot.bot.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.*;
import org.info.tg_bot.model.enums.ActionCategory;
import org.info.tg_bot.model.enums.CancellationType;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.info.tg_bot.repository.BranchRepository;
import org.info.tg_bot.repository.DepartmentRepository;
import org.info.tg_bot.repository.PositionRepository;
import org.info.tg_bot.service.*;
import org.info.tg_bot.service.AuditLogService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
    private final AuditLogService auditLogService;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final BranchRepository branchRepository;

    private static final int DEPARTMENTS_PER_PAGE = 5;
    private static final int POSITIONS_PER_PAGE = 5;

    // ---------- /start ----------
    public SendMessage handleStartCommand(Long chatId, Long telegramId, String username) {
        if (userService.existsByTelegramId(telegramId)) {
            User user = userService.findByTelegramId(telegramId).orElseThrow();
            auditLogService.logAction(
                    user,
                    "LOGIN",
                    ActionCategory.AUTH,
                    "User",
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
                    Long id = Long.parseLong(data.substring(5));
                    Department department = departmentRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("Department not found"));
                    userService.updateRegistrationDepartment(telegramId, department.getName());
                    return createBranchInlineKeyboard(chatId, 0);
                } else if (data.startsWith("page:")) {
                    int page = Integer.parseInt(data.substring(5));
                    return createDepartmentInlineKeyboard(chatId, page);
                } else {
                    Department department = departmentRepository.findByName(data)
                            .orElseThrow(() -> new RuntimeException("Department not found"));
                    userService.updateRegistrationDepartment(telegramId, department.getName());
                    return createBranchInlineKeyboard(chatId, 0);
                }
            }
            case "BRANCH" -> {
                if (data.startsWith("branch:")) {
                    Long id = Long.parseLong(data.substring(7));
                    Branch branch = branchRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("Branch not found"));
                    userService.updateRegistrationBranch(telegramId, branch.getName());
                    return createPositionInlineKeyboard(chatId, 0);
                } else if (data.startsWith("branchpage:")) {
                    int page = Integer.parseInt(data.substring(11));
                    return createBranchInlineKeyboard(chatId, page);
                } else {
                    Branch branch = branchRepository.findByName(data)
                            .orElseThrow(() -> new RuntimeException("Branch not found"));
                    userService.updateRegistrationBranch(telegramId, branch.getName());
                    return createPositionInlineKeyboard(chatId, 0);
                }
            }
            case "POSITION" -> {
                if (data.startsWith("pos:")) {
                    Long id = Long.parseLong(data.substring(4));
                    Position position = positionRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("Position not found"));
                    userService.updateRegistrationPosition(telegramId, position.getName());
                    return new SendMessage(chatId.toString(), "Пожалуйста, введите ваше имя:");
                } else if (data.startsWith("pospage:")) {
                    int page = Integer.parseInt(data.substring(8));
                    return createPositionInlineKeyboard(chatId, page);
                } else {
                    Position position = positionRepository.findByName(data)
                            .orElseThrow(() -> new RuntimeException("Position not found"));
                    userService.updateRegistrationPosition(telegramId, position.getName());
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
                auditLogService.logAction(
                        user,
                        "REGISTER_USER",
                        ActionCategory.USER,
                        "User",
                        user.getId(),
                        "User registration completed",
                        null,
                        null,
                        "Telegram Bot",
                        user.getTelegramUsername(),
                        true,
                        null,
                        Map.of("username", user.getTelegramUsername(), "fullName", user.getFullName())
                        );
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
        List<Department> departments = departmentRepository.findAll();
        int start = page * DEPARTMENTS_PER_PAGE;
        int end = Math.min(start + DEPARTMENTS_PER_PAGE, departments.size());

        SendMessage message = new SendMessage(chatId.toString(),
                "Пожалуйста, выберите ваш отдел из списка или напишите его вручную:");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = start; i < end; i++) {
            Department dept = departments.get(i);
            rows.add(List.of(createButton(dept.getName(), "dept:" + i)));
        }

        addPagination(rows, page, departments.size(), "page:");
        message.setReplyMarkup(new InlineKeyboardMarkup(rows));
        return message;
    }

    private SendMessage createBranchInlineKeyboard(Long chatId, int page) {
        List<Branch> branches = branchRepository.findAll();
        int start = page * 5;
        int end = Math.min(start + 5, branches.size());

        SendMessage message = new SendMessage(chatId.toString(),
                "Пожалуйста, выберите ваше управление из списка или введите вручную:");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = start; i < end; i++) {
            Branch branch = branches.get(i);
            rows.add(List.of(createButton(branch.getName(), "branch:" + i)));
        }

        addPagination(rows, page, branches.size(), "branchpage:");
        message.setReplyMarkup(new InlineKeyboardMarkup(rows));
        return message;
    }

    private SendMessage createPositionInlineKeyboard(Long chatId, int page) {
        List<Position> positions = positionRepository.findAll();
        int start = page * POSITIONS_PER_PAGE;
        int end = Math.min(start + POSITIONS_PER_PAGE, positions.size());

        SendMessage message = new SendMessage(chatId.toString(),
                "Пожалуйста, выберите вашу должность из списка или введите вручную:");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = start; i < end; i++) {
            Position pos = positions.get(i);
            rows.add(List.of(createButton(pos.getName(), "pos:" + i)));
        }

        addPagination(rows, page, positions.size(), "pospage:");
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
        auditLogService.logAction(
                user,
                "VIEW_TASKS",
                ActionCategory.DOCUMENT,
                "Document",
                null,
                "User viewed their tasks",
                null,
                null,
                "Telegram Bot",
                user.getTelegramUsername(),
                true,
                null,
                Map.of("taskCount", activeDocuments.size())
                );

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");
        return message;
    }

    // ---------- Обработка callback-кнопок ----------
    public SendMessage handleCallback(Long chatId, Long telegramId, String callbackData) {
        if (userService.isWaitingForTaskNumber(telegramId)) {
            userService.clearWaitingForTaskNumber(telegramId);
        }
        Optional<User> optionalUser = userService.findByTelegramId(telegramId);
        if (optionalUser.isEmpty()) {
            return new SendMessage(chatId.toString(), "❌ Вы не зарегистрированы. Используйте /start.");
        }
        User user = optionalUser.get();

        // --- Отправка результата ---
        if (callbackData.startsWith("send_result:")) {
            String regNumber = callbackData.substring("send_result:".length());
            Optional<Document> optionalDoc = documentService.findByRegNumber(regNumber);

            if (optionalDoc.isEmpty()) {
                return new SendMessage(chatId.toString(), "⚠ Документ с таким номером не найден.");
            }

            Document doc = optionalDoc.get();
            userService.setWaitingForFile(telegramId, doc.getId());
            auditLogService.logAction(
                    user,
                    "REQUEST_SUBMIT_RESULT",
                    ActionCategory.RESULT,
                    "Document",
                    doc.getId(),
                    "User requested to submit result",
                    null,
                    null,
                    "Telegram Bot",
                    user.getTelegramUsername(),
                    true,
                    null,
                    null
            );

            return new SendMessage(chatId.toString(),
                    "📤 Пожалуйста, отправьте файл/текст для поручения №" + regNumber + ".");
        }

        // --- Запрос на продление ---
        if (callbackData.startsWith("request_extend:")) {
            String regNumber = callbackData.substring("request_extend:".length());
            Optional<Document> optionalDoc = documentService.findByRegNumber(regNumber);
            if (optionalDoc.isEmpty()) {
                return new SendMessage(chatId.toString(), "⚠ Документ с таким номером не найден.");
            }

            Document doc = optionalDoc.get();
            userService.setWaitingForExtensionReason(telegramId, doc.getId());
            auditLogService.logAction(
                    user,
                    "REQUEST_EXTENSION",
                    ActionCategory.EXTENSION,
                    "Document",
                    doc.getId(),
                    "User requested extension",
                    null,
                    null,
                    "Telegram Bot",
                    user.getTelegramUsername(),
                    true,
                    null,
                    null
            );

            return new SendMessage(chatId.toString(),
                    "💬 Пожалуйста, укажите причину запроса на продление поручения №" + regNumber + ":");
        }

        return new SendMessage(chatId.toString(), "Неизвестное действие.");
    }

    // ---------- Обработка причины продления ----------
    public SendMessage handleExtensionReasonInput(Long chatId, Long telegramId, String reason) {
        if (!userService.isWaitingForExtensionReason(telegramId)) {
            return new SendMessage(chatId.toString(), "⚠ Нет активного запроса на продление.");
        }

        Optional<Long> documentIdOpt = userService.consumeWaitingForExtensionReason(telegramId);
        if (documentIdOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Документ не найден.");
        }

        Long documentId = documentIdOpt.get();
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
        User boss = userService.findBoss(user.getTelegramId());

        if (boss == null || boss.getTelegramId() == null) {
            return new SendMessage(chatId.toString(), "⚠ Не удалось определить начальника для отправки запроса.");
        }

        try {
            ExtensionRequest request = extensionRequestService.createRequest(
                    doc,
                    user,
                    LocalDateTime.now(),
                    reason
            );

            auditLogService.logAction(
                    user,
                    "CREATE_EXTENSION_REQUEST",
                    ActionCategory.EXTENSION,
                    "ExtensionRequest",
                    request.getId(),
                    "Extension request created",
                    null,
                    Map.of("documentId", doc.getId(), "reason", reason),
                    "Telegram Bot",
                    user.getTelegramUsername(),
                    true,
                    null,
                    null
            );

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

            notificationService.notifyUser(boss.getTelegramId(), notificationText, keyboard);

            return new SendMessage(chatId.toString(),
                    "✅ Запрос на продление отправлен руководителю. Ожидайте ответа.");

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

    public SendMessage handleFileUpload(Long chatId, Long telegramId, org.telegram.telegrambots.meta.api.objects.Document file) {
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
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            keyboard.setKeyboard(List.of(
                    List.of(
                            InlineKeyboardButton.builder()
                                    .text("Поручение исполнено")
                                    .callbackData("task_completed:" + doc.getRegNumber())
                                    .build(),
                            InlineKeyboardButton.builder()
                                    .text("Отправить на доработку")
                                    .callbackData("send_for_revision:" + doc.getRegNumber())
                                    .build()
                    )
            ));
            fileForwardingService.sendDocumentToAdmin(
                    admin.getTelegramId(),
                    caption,
                    file.getFileId(),
                    file.getFileName(),
                    doc.getRegNumber(),
                    keyboard
            );

            auditLogService.logAction(
                    executor,
                    "SUBMIT_FILE_RESULT",
                    ActionCategory.RESULT,
                    "Document",
                    doc.getId(),
                    "User submitted file result",
                    null,
                    null,
                    "Telegram Bot",
                    executor.getTelegramUsername(),
                    true,
                    null,
                    Map.of("fileName", file.getFileName())
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

    public SendMessage handleTextResultSubmission(Long chatId, Long telegramId, String resultText) {
        try {
            Long documentId = userService.getDocumentIdWaitingForFile(telegramId);
            if (documentId == null) {
                return new SendMessage(chatId.toString(), "⚠ Не найдено поручение для отправки результата. Попробуйте снова.");
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
                return new SendMessage(chatId.toString(), "⚠ Администратор не найден. Сообщение не было отправлено.");
            }

            Optional<User> userOpt = userService.findByTelegramId(telegramId);
            if (userOpt.isEmpty()) {
                userService.clearWaitingForFile(telegramId);
                return new SendMessage(chatId.toString(), "⚠ Пользователь не найден.");
            }

            User executor = userOpt.get();

            // 📩 Формируем сообщение для администратора
            String message = String.format(
                    "📬 <b>Результат выполнения поручения</b>\n\n" +
                            "👤 Исполнитель: %s\n" +
                            "📄 Поручение №%s\n" +
                            "📝 Описание: %s\n" +
                            "🗓 Срок исполнения: %s\n" +
                            "💬 Текст результата:\n%s",
                    executor.getFullName(),
                    doc.getRegNumber(),
                    doc.getSummary(),
                    doc.getExecutionDate(),
                    resultText
            );
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            keyboard.setKeyboard(List.of(
                    List.of(
                            InlineKeyboardButton.builder()
                                    .text("Поручение исполнено")
                                    .callbackData("task_completed:" + doc.getRegNumber())
                                    .build(),
                            InlineKeyboardButton.builder()
                                    .text("Отправить на доработку")
                                    .callbackData("send_for_revision:" + doc.getRegNumber())
                                    .build()
                    )
            ));
            notificationService.notifyAdmin(admin.getTelegramId(), message, keyboard);

            auditLogService.logAction(
                    executor,
                    "SUBMIT_TEXT_RESULT",
                    ActionCategory.RESULT,
                    "Document",
                    doc.getId(),
                    "User submitted text result",
                    null,
                    null,
                    "Telegram Bot",
                    executor.getTelegramUsername(),
                    true,
                    null,
                    Map.of("resultText", resultText)
                    );

            doc.setStatus(DocumentStatus.PENDING);
            documentService.save(doc);

            userService.clearWaitingForFile(telegramId);

            log.info("✅ Text result sent from user {} to admin {} for document {}",
                    telegramId, admin.getTelegramId(), doc.getRegNumber());

            return new SendMessage(chatId.toString(), "✅ Текстовый результат успешно отправлен администратору на рассмотрение.");

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке текстового результата: {}", e.getMessage(), e);
            return new SendMessage(chatId.toString(), "⚠ Ошибка при отправке результата. Попробуйте позже.");
        }
    }

    // ---------- Проверка состояний ----------
    // Removed isWaitingForExtensionReason as it's now in UserService

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

    public SendMessage handlePendingRequests(Long chatId, Long bossTelegramId) {
        Optional<User> bossOpt = userService.findByTelegramId(bossTelegramId);
        if (bossOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Руководитель не найден.");
        }

        User boss = bossOpt.get();
        List<User> supervisedUsers = userService.findSupervisedUsers(bossTelegramId);
        List<ExtensionRequest> pending = extensionRequestService.findPendingRequestsByUsers(supervisedUsers);

        if (pending.isEmpty()) {
            return new SendMessage(chatId.toString(), "📭 Нет ожидающих запросов на продление от ваших подчинённых.");
        }

        StringBuilder text = new StringBuilder("📋 <b>Ваши запросы на продление:</b>\n\n");

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
                    Map.of("documentId", request.getDocument().getId(), "reason", "Request rejected by boss")
                    );

            extensionRequestService.rejectRequest(
                    request,
                    admin,
                    "Запрос отклонён начальником"
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