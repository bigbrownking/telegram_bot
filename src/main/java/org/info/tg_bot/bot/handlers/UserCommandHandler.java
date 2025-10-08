package org.info.tg_bot.bot.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.service.UserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCommandHandler {

    private final UserService userService;

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
            "Председатель",
            "Первый заместитель Председателя",
            "Заместитель Председателя по оперативной работе",
            "Руководитель аппарата",
            "Руководитель департамента",
            "Заместитель руководителя департамента",
            "Руководитель управления",
            "Заместитель руководителя управления",
            "Следователь по особо важным делам",
            "Старший следователь",
            "Советник Председателя",
            "Помощник Председателя",
            "Помощник Председателя по режиму",
            "Руководитель управления А",
            "Главный консультант А",
            "Главный эксперт А",
            "Эксперт А",
            "Оперуполномоченный (дознаватель) по особо важным делам",
            "Старший оперуполномоченный (дознаватель)",
            "Главный криминалист",
            "Старший криминалист",
            "Криминалист (за счет Старшего криминалиста)",
            "Заведующий складом",
            "Заведующий хозяйством",
            "Административный секретарь",
            "Статистик",
            "Архивариус",
            "Делопроизводитель",
            "Курьер",
            "Секретарь",
            "Столяр",
            "Электромонтер-техник по связи",
            "Слесарь-сантехник",
            "Электрик",
            "Кадровый резерв",
            "Откомандирование"
    );

    private static final int DEPARTMENTS_PER_PAGE = 5;
    private static final int POSITIONS_PER_PAGE = 5;

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
            return createPositionInlineKeyboard(chatId, 0);

        } else if (currentStep.equals("DEPARTMENT") && data.startsWith("page:")) {
            int page = Integer.parseInt(data.substring(5));
            return createDepartmentInlineKeyboard(chatId, page);

        } else if (currentStep.equals("POSITION") && data.startsWith("pos:")) {
            int index = Integer.parseInt(data.substring(4));
            String position = POSITIONS.get(index);
            userService.updateRegistrationPosition(telegramId, position);
            return new SendMessage(chatId.toString(), "Пожалуйста, введите ваше имя:");

        } else if (currentStep.equals("POSITION") && data.startsWith("pospage:")) {
            int page = Integer.parseInt(data.substring(8));
            return createPositionInlineKeyboard(chatId, page);

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

    private SendMessage createPositionInlineKeyboard(Long chatId, int page) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Пожалуйста, выберите вашу должность:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        int start = page * POSITIONS_PER_PAGE;
        int end = Math.min(start + POSITIONS_PER_PAGE, POSITIONS.size());
        for (int i = start; i < end; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createButton(POSITIONS.get(i), "pos:" + i));
            rows.add(row);
        }

        List<InlineKeyboardButton> paginationRow = new ArrayList<>();
        if (page > 0) {
            paginationRow.add(createButton("⬅ Назад", "pospage:" + (page - 1)));
        }
        if (end < POSITIONS.size()) {
            paginationRow.add(createButton("Вперед ➡", "pospage:" + (page + 1)));
        }
        if (!paginationRow.isEmpty()) {
            rows.add(paginationRow);
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

    public SendMessage handleMyTasksCommand(Long chatId, Long telegramId) {
        Optional<User> optionalUser = userService.findByTelegramId(telegramId);
        if (optionalUser.isEmpty()) {
            return new SendMessage(chatId.toString(), "❌ Вы не зарегистрированы. Используйте /start.");
        }

        User user = optionalUser.get();

        if (user.getDocuments() == null || user.getDocuments().isEmpty()) {
            return new SendMessage(chatId.toString(), "📭 У вас пока нет назначенных документов.");
        }

        StringBuilder sb = new StringBuilder("📋 *Ваши задачи:*\n\n");
        int counter = 1;
        for (Document doc : user.getDocuments()) {
            sb.append(String.format("%d️⃣ %s\n", counter++,
                    doc.getSummary() != null ? doc.getSummary() : "(без содержания)"));
            sb.append(String.format("Рег. №: %s\n", doc.getRegNumber() != null ? doc.getRegNumber() : "—"));
            sb.append(String.format("Подразделение: %s\n", doc.getDepartment() != null ? doc.getDepartment() : "—"));
            sb.append(String.format("Срок исполнения: %s\n", doc.getExecutionDate() != null ? doc.getExecutionDate() : "не указан"));
            sb.append("\n\n");
        }

        SendMessage message = new SendMessage(chatId.toString(), sb.toString());
        message.setParseMode("Markdown");
        return message;
    }

    public SendMessage handleHelpCommand(Long chatId) {
        String helpText = "Доступные команды:\n" +
                "/start - Зарегистрироваться или начать работу\n" +
                "/mytasks - Посмотреть ваши задачи\n" +
                "/help - Показать это сообщение";
        return new SendMessage(chatId.toString(), helpText);
    }
}