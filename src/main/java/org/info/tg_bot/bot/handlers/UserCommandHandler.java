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
import org.info.tg_bot.util.KeyBoardUtil;
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

    private final KeyBoardUtil keyBoardUtil;

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

        return switch (currentStep) {
            case "DEPARTMENT" -> {
                if (data.startsWith("dept:")) {
                    Long id = Long.parseLong(data.substring(5));
                    Department dept = departmentRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("Department not found"));
                    userService.updateRegistrationDepartment(telegramId, dept.getName());
                    yield createBranchInlineKeyboard(chatId, 0);
                } else if (data.startsWith("page:")) {
                    int page = Integer.parseInt(data.substring(5));
                    yield createDepartmentInlineKeyboard(chatId, page);
                } else {
                    Department dept = departmentRepository.findByName(data)
                            .orElseThrow(() -> new RuntimeException("Department not found"));
                    userService.updateRegistrationDepartment(telegramId, dept.getName());
                    yield createBranchInlineKeyboard(chatId, 0);
                }
            }

            case "BRANCH" -> {
                if (data.startsWith("branch:")) {
                    Long id = Long.parseLong(data.substring(7));
                    Branch branch = branchRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("Branch not found"));
                    String branchName = branch.getName().equals("ПРОПУСТИТЬ") ? "—" : branch.getName();
                    userService.updateRegistrationBranch(telegramId, branchName);
                    yield createPositionInlineKeyboard(chatId, 0);
                } else if (data.startsWith("branchpage:")) {
                    int page = Integer.parseInt(data.substring(11));
                    yield createBranchInlineKeyboard(chatId, page);
                } else {
                    Branch branch = branchRepository.findByName(data)
                            .orElseThrow(() -> new RuntimeException("Branch not found"));
                    userService.updateRegistrationBranch(telegramId, branch.getId() == 47L ? null : branch.getName());
                    yield createPositionInlineKeyboard(chatId, 0);
                }
            }

            case "POSITION" -> {
                if (data.startsWith("pos:")) {
                    Long id = Long.parseLong(data.substring(4));
                    Position pos = positionRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("Position not found"));
                    userService.updateRegistrationPosition(telegramId, pos.getName());
                } else if (data.startsWith("pospage:")) {
                    int page = Integer.parseInt(data.substring(8));
                    yield createPositionInlineKeyboard(chatId, page);
                } else {
                    Position pos = positionRepository.findByName(data)
                            .orElseThrow(() -> new RuntimeException("Position not found"));
                    userService.updateRegistrationPosition(telegramId, pos.getName());
                }
                yield new SendMessage(chatId.toString(), "Пожалуйста, введите ваше имя как в удостоверении:");
            }

            case "NAME" -> {
                if (data.isBlank()) {
                    yield new SendMessage(chatId.toString(), "Имя не может быть пустым. Введите ещё раз:");
                }
                userService.updateRegistrationName(telegramId, data);
                yield new SendMessage(chatId.toString(), "Пожалуйста, введите вашу фамилию как в удостоверении:");
            }

            case "SURNAME" -> {
                if (data.isBlank()) {
                    yield new SendMessage(chatId.toString(), "Фамилия не может быть пустой. Введите ещё раз:");
                }
                userService.updateRegistrationSurname(telegramId, data);
                yield createFatherNameKeyboard(chatId); // Клавиатура с "Пропустить"
            }

            case "FATHERNAME" -> {
                String fatherName = "skip_fathername".equals(data) ? null : data.strip();

                // Если ввели только пробелы — тоже пропускаем
                if (fatherName != null && fatherName.isBlank()) {
                    fatherName = null;
                }

                userService.completeRegistration(telegramId, fatherName);

                User user = userService.findByTelegramId(telegramId).orElseThrow();
                String tgUsername = user.getTelegramUsername();

                auditLogService.logAction(
                        user, "REGISTER_USER", ActionCategory.USER, "User", user.getId(),
                        "User registration completed", null, null, "Telegram Bot",
                        tgUsername, true, null,
                        Map.of(
                                "username", tgUsername != null ? tgUsername : "-",
                                "fullName", user.getFullName(),
                                "hasFatherName", fatherName != null ? "yes" : "no"
                        )
                );

                yield new SendMessage(chatId.toString(),
                        String.format("Регистрация завершена! Добро пожаловать, %s!", user.getFullName()));
            }

            default -> new SendMessage(chatId.toString(), "Ошибка регистрации. Используйте /start для начала.");
        };
    }

    // ---------- Выбор отдела / должности ----------
    private SendMessage createDepartmentInlineKeyboard(Long chatId, int page) {
        List<Department> departments = departmentRepository.findAll();
        SendMessage message = new SendMessage(chatId.toString(),
                "Пожалуйста, выберите ваш отдел из списка или напишите его вручную:");
        message.setReplyMarkup(keyBoardUtil.createDepartmentKeyboard(departments, page));
        return message;
    }

    private SendMessage createBranchInlineKeyboard(Long chatId, int page) {
        List<Branch> branches = branchRepository.findAllWithSpecialFirst();

        SendMessage message = new SendMessage(chatId.toString(),
                "Пожалуйста, выберите ваше управление из списка или введите вручную:");
        message.setReplyMarkup(keyBoardUtil.createBranchKeyboard(branches, page));
        return message;
    }

    private SendMessage createPositionInlineKeyboard(Long chatId, int page) {
        List<Position> positions = positionRepository.findAll();
        SendMessage message = new SendMessage(chatId.toString(),
                "Пожалуйста, выберите вашу должность из списка или введите вручную:");
        message.setReplyMarkup(keyBoardUtil.createPositionKeyboard(positions, page));
        return message;
    }
    private SendMessage createFatherNameKeyboard(Long chatId) {
        SendMessage message = new SendMessage(chatId.toString(),
                "Пожалуйста, введите ваше отчество как в удостоверении:\n\n<i>Если отчества нет — нажмите «Пропустить»</i>");
        message.enableHtml(true);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton skipButton = new InlineKeyboardButton("Пропустить");
        skipButton.setCallbackData("skip_fathername");

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(skipButton);
        rows.add(row);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        return message;
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
                    null,
                    "Telegram Bot",
                    user.getTelegramUsername(),
                    true,
                    null,
                    Map.of("documentId", doc.getId(), "reason", reason)
            );

            String notificationText = String.format(
                    """
                            📩 <b>Запрос на продление</b>
    
                            👤 Исполнитель: %s
                            📄 Поручение №%s
                            📝 Описание: %s
                            🗓 Текущий срок: %s
                            💬 Причина: %s""",
                    user.getFullName(),
                    doc.getRegNumber(),
                    doc.getSummary(),
                    doc.getExecutionDate(),
                    reason
            );

            notificationService.notifyUser(boss.getTelegramId(), notificationText, keyBoardUtil.createPendingRequestsKeyboard(List.of(request)));

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

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyBoardUtil.createUserTaskActionsKeyboard(doc.getRegNumber()))
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
                    """
                            📬 <b>Результат выполнения поручения</b>
    
                            👤 Исполнитель: %s
                            📄 Поручение №%s
                            📝 Описание: %s
                            🗓 Срок исполнения: %s
                            📎 Файл: %s""",
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
                    doc.getRegNumber(),
                    keyBoardUtil.createFileResultKeyboard(doc.getRegNumber())
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

            String message = String.format(
                    """
                            📬 <b>Результат выполнения поручения</b>
    
                            👤 Исполнитель: %s
                            📄 Поручение №%s
                            📝 Описание: %s
                            🗓 Срок исполнения: %s
                            💬 Текст результата:
                            %s""",
                    executor.getFullName(),
                    doc.getRegNumber(),
                    doc.getSummary(),
                    doc.getExecutionDate(),
                    resultText
            );

            notificationService.notifyAdmin(admin.getTelegramId(), message, keyBoardUtil.createFileResultKeyboard(doc.getRegNumber()));

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

    // ---------- /help ----------
    public SendMessage handleHelpCommand(Long chatId) {
        String helpText = """
                Доступные команды:
                /start - Зарегистрироваться или начать работу
                /tasks - Посмотреть ваши задачи
                /pending - Запросы на продление (для руководителей)
                /help - Показать это сообщение
                """;
        return new SendMessage(chatId.toString(), helpText);
    }

    public SendMessage handlePendingRequests(Long chatId, Long bossTelegramId) {
        Optional<User> bossOpt = userService.findByTelegramId(bossTelegramId);
        if (bossOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Руководитель не найден.");
        }

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

        SendMessage message = new SendMessage(chatId.toString(), text.toString());
        message.enableHtml(true);
        message.setReplyMarkup(keyBoardUtil.createPendingRequestsKeyboard(pending));
        return message;
    }

    public SendMessage handleApproveExtendRequest(Long chatId, Long requestId, Long bossTelegramId) {
        Optional<ExtensionRequest> requestOpt = extensionRequestService.findById(requestId);
        if (requestOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Запрос не найден.");
        }

        ExtensionRequest request = requestOpt.get();
        Optional<User> bossOpt = userService.findByTelegramId(bossTelegramId);

        if (bossOpt.isEmpty()) {
            return new SendMessage(chatId.toString(), "⚠ Руководитель не найден.");
        }
        User boss = bossOpt.get();
        Document doc = request.getDocument();

        String message = String.format(
                """
                        🗓 <b>Запрос на продление отправлен админу</b>
    
                        📄 Поручение №%s
                        👤 Исполнитель: %s
                        📅 Текущий срок: %s
                        💬 Причина: %s
    
                        """,
                request.getDocument().getRegNumber(),
                request.getUser().getFullName(),
                request.getDocument().getExecutionDate(),
                request.getReason() != null ? request.getReason() : "—"
        );

        SendMessage sendMessage = new SendMessage(chatId.toString(), message);
        sendMessage.enableHtml(true);

        extensionRequestService.sendToAdminRequest(
                request,
                boss,
                "Ожидание админа на продление",
                doc.getExecutionDate(),
                null
        );

        User adminUploader = doc.getUploadedBy();
        if (adminUploader != null && adminUploader.getTelegramId() != null) {
            notificationService.notifyAdmin(
                    adminUploader.getTelegramId(),
                    String.format(
                            """
                                    🔔 <b>Запрос на продление</b>
    
                                    📄 Поручение №%s
                                    👤 Исполнитель: %s
                                    🗓 Текущий срок: %s
                                    👤 Одобрил: %s
                                    💬 Причина: %s""",
                            doc.getRegNumber(),
                            request.getUser().getFullName(),
                            doc.getExecutionDate(),
                            boss.getFullName(),
                            request.getReason() != null ? request.getReason() : "—"
                    ),
                    keyBoardUtil.createPendingRequestsKeyboard(List.of(request))
            );
            log.info("🔔 Notification sent to admin {} about extension of document {}",
                    adminUploader.getTelegramId(), doc.getRegNumber());
        } else {
            log.warn("No admin uploader found for document {}", doc.getRegNumber());
        }
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
                            """
                                    ❌ <b>Запрос отклонён</b>

                                    📄 Поручение №%s
                                    📝 Описание: %s
                                    🗓 Срок исполнения: %s
                                    👤 Отклонил: %s

                                    Пожалуйста, выполните поручение в установленный срок.""",
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

}