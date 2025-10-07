package org.info.tg_bot.bot.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.service.DocumentParserService;
import org.info.tg_bot.service.UserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCommandHandler {

    private final UserService userService;
    private final DocumentParserService documentParserService;

    private static final List<String> DEPARTMENTS = Arrays.asList(
            "Руководство",
            "Следственный департамент",
            "Секретариат Председателя",
            "Оперативный департамент",
            "Департамент превенции и аналитических разработок",
            "Департамент финансового мониторинга в сфере отмывания доходов, поиска и возврата преступных активов",
            "Департамент антикоррупционного финансового мониторинга",
            "Департамент финансового мониторинга в сфере финансирования терроризма, наркобизнеса и распространения оружия массового уничтожения",
            "Департамент оперативного анализа",
            "Департамент по работе с субъектами финансового мониторинга",
            "Департамент собственной безопасности",
            "Департамент кадровой работы",
            "Департамент стратегии и оперативного управления",
            "Департамент правого обеспечения",
            "Департамент цифровизации",
            "Административный департамент",
            "Криминалистическое управление",
            "Управление по работе со средствами массовой информации",
            "Служба внутреннего аудита",
            "Внештатные сотрудники",
            "В распоряжении",
            "Кадровый резерв",
            "Откомандирование"
    );

    private static final List<String> POSITIONS = Arrays.asList(
            "Руководитель",
            "Заместитель руководителя",
            "Специалист",
            "Аналитик",
            "Менеджер",
            "Оперативный сотрудник"
    );

    private static final int DEPARTMENTS_PER_PAGE = 5;

    public SendMessage handleStartCommand(Long chatId, Long telegramId, String username) {
        if (userService.existsByTelegramId(telegramId)) {
            User user = userService.findByTelegramId(telegramId).orElseThrow();
            return new SendMessage(chatId.toString(),
                    String.format("С возвращением, %s!", user.getName()));
        }

        userService.startRegistration(telegramId, username);
        return createDepartmentInlineKeyboard(chatId, 0);
    }

    public SendMessage handleRegistrationStep(Long chatId, Long telegramId, String data) {
        if (userService.existsByTelegramId(telegramId)) {
            return new SendMessage(chatId.toString(), "Вы уже зарегистрированы.");
        }
        String currentStep = userService.getRegistrationStep(telegramId);
        if (currentStep.isEmpty()) {
            return new SendMessage(chatId.toString(), "Ошибка регистрации. Используйте /start для начала.");
        }

        if (currentStep.equals("DEPARTMENT") && data.startsWith("dept:")) {
            int index = Integer.parseInt(data.substring(5));
            String department = DEPARTMENTS.get(index);
            userService.updateRegistrationDepartment(telegramId, department);
            return createPositionInlineKeyboard(chatId);
        } else if (currentStep.equals("DEPARTMENT") && data.startsWith("page:")) {
            int page = Integer.parseInt(data.substring(5));
            return createDepartmentInlineKeyboard(chatId, page);
        } else if (currentStep.equals("POSITION") && data.startsWith("pos:")) {
            String position = data.substring(4);
            if (!isValidPosition(position)) {
                return new SendMessage(chatId.toString(), "Недопустимая должность. Пожалуйста, выберите из предложенных вариантов.");
            }
            userService.updateRegistrationPosition(telegramId, position);
            return new SendMessage(chatId.toString(), "Пожалуйста, введите ваше имя:");
        } else if (currentStep.equals("NAME")) {
            userService.updateRegistrationName(telegramId, data);
            return new SendMessage(chatId.toString(), "Пожалуйста, введите вашу фамилию:");
        } else if (currentStep.equals("SURNAME")) {
            userService.updateRegistrationSurname(telegramId, data);
            return new SendMessage(chatId.toString(), "Пожалуйста, введите ваше отчество:");
        } else if (currentStep.equals("FATHERNAME")) {
            userService.completeRegistration(telegramId, data);
            User user = userService.findByTelegramId(telegramId).orElseThrow();
            return new SendMessage(chatId.toString(),
                    String.format("Регистрация завершена! Добро пожаловать, %s!", user.getFullName()));
        }

        return new SendMessage(chatId.toString(), "Ошибка регистрации. Используйте /start для начала.");
    }

    public SendMessage createDepartmentInlineKeyboard(Long chatId, int page) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Пожалуйста, выберите ваш отдел:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        int start = page * DEPARTMENTS_PER_PAGE;
        int end = Math.min(start + DEPARTMENTS_PER_PAGE, DEPARTMENTS.size());
        for (int i = start; i < end; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createButton(DEPARTMENTS.get(i), "dept:" + i));
            rows.add(row);
        }

        List<InlineKeyboardButton> paginationRow = new ArrayList<>();
        if (page > 0) {
            paginationRow.add(createButton("⬅ Назад", "page:" + (page - 1)));
        }
        if (end < DEPARTMENTS.size()) {
            paginationRow.add(createButton("Вперед ➡", "page:" + (page + 1)));
        }
        if (!paginationRow.isEmpty()) {
            rows.add(paginationRow);
        }

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        return message;
    }

    private SendMessage createPositionInlineKeyboard(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Пожалуйста, выберите вашу должность:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (String position : POSITIONS) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createButton(position, "pos:" + position));
            rows.add(row);
        }

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        return message;
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private boolean isValidDepartment(String department) {
        return DEPARTMENTS.contains(department);
    }

    private boolean isValidPosition(String position) {
        return POSITIONS.contains(position);
    }

    public SendMessage handleMyTasksCommand(Long chatId, Long telegramId) {
        return new SendMessage(chatId.toString(), "Функционал задач пока не реализован.");
    }

    public SendMessage handleDocumentDetails(Long chatId, Long documentId, Long telegramId) {
        return new SendMessage(chatId.toString(), "Функционал деталей документа пока не реализован.");
    }

    public SendMessage handleExcelUpload(Long chatId, Long telegramId, String fileName, String fileId, TelegramLongPollingBot bot) {
        try {
            User user = userService.findByTelegramId(telegramId).orElse(null);
            if (user == null) {
                return new SendMessage(chatId.toString(), "❌ Вы не зарегистрированы. Используйте /start для регистрации.");
            }

            // if (!user.isAdmin()) {
            //     return new SendMessage(chatId.toString(), "❌ Только администраторы могут загружать документы.");
            // }

            if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
                return new SendMessage(chatId.toString(), "❌ Неверный формат. Пожалуйста, отправьте файл Excel (.xlsx или .xls).");
            }

            GetFile getFile = new GetFile();
            getFile.setFileId(fileId);
            File tgFile = bot.execute(getFile);
            String filePath = tgFile.getFilePath();

            try (InputStream inputStream = bot.downloadFileAsStream(filePath)) {
                List<Document> documents = documentParserService.parse(inputStream, user);
                log.info("✅ User {} uploaded {}: parsed {} documents", telegramId, fileName, documents.size());
                return new SendMessage(chatId.toString(),
                        String.format("✅ Файл \"%s\" успешно обработан! Загружено %d документов.", fileName, documents.size()));
            }

        } catch (Exception e) {
            log.error("❌ Ошибка загрузки Excel файла для пользователя {}: {}", telegramId, e.getMessage(), e);
            return new SendMessage(chatId.toString(), "⚠ Произошла ошибка при обработке файла. Попробуйте снова.");
        }
    }

    public SendMessage handleRequestExtension(Long chatId, Long documentId) {
        return new SendMessage(chatId.toString(), "Функционал продления пока не реализован.");
    }

    public SendMessage handleSubmitResult(Long chatId, Long documentId) {
        return new SendMessage(chatId.toString(), "Функционал отправки результата пока не реализован.");
    }

    public SendMessage handleUploadCommand(Long chatId) {
        String text = "📤 Пожалуйста, отправьте Excel-файл (.xlsx или .xls).";
        return new SendMessage(chatId.toString(), text);
    }

    public SendMessage handleHelpCommand(Long chatId) {
        String helpText = "Доступные команды:\n" +
                "/start - Зарегистрироваться или начать работу\n" +
                "/mytasks - Посмотреть ваши задачи\n" +
                "/upload - Загрузить excel файл\n" +
                "/help - Показать это сообщение";
        return new SendMessage(chatId.toString(), helpText);
    }
}