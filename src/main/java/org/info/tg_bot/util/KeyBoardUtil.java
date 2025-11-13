package org.info.tg_bot.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.info.tg_bot.model.entity.Department;
import org.info.tg_bot.model.entity.Position;
import org.info.tg_bot.model.entity.Branch;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.ExtensionRequest;
import org.info.tg_bot.model.enums.CancellationType;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Getter
@Component
@RequiredArgsConstructor
public class KeyBoardUtil {

    private static final int DEPARTMENTS_PER_PAGE = 5;
    private static final int POSITIONS_PER_PAGE = 5;
    private static final int BRANCHES_PER_PAGE = 5;

    public InlineKeyboardMarkup createTaskFilterKeyboard(Map<DocumentStatus, List<Document>> groupedByStatus) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(
                createButton("🆕 В работе (" + groupedByStatus.getOrDefault(DocumentStatus.IN_PROGRESS, List.of()).size() + ")", "tasks_filter:IN_PROGRESS"),
                createButton("⏳ На рассмотрении (" + groupedByStatus.getOrDefault(DocumentStatus.PENDING, List.of()).size() + ")", "tasks_filter:PENDING")
        ));
        keyboard.add(List.of(
                createButton("🔄 На доработке (" + groupedByStatus.getOrDefault(DocumentStatus.REVISION, List.of()).size() + ")", "tasks_filter:REVISION"),
                createButton("✅ Исполнено (" + groupedByStatus.getOrDefault(DocumentStatus.COMPLETED, List.of()).size() + ")", "tasks_filter:COMPLETED")
        ));
        keyboard.add(List.of(
                createButton("❌ Отменено (" + groupedByStatus.getOrDefault(DocumentStatus.CANCELLED, List.of()).size() + ")", "tasks_filter:CANCELLED"),
                createButton("📋 Все", "tasks_filter:ALL")
        ));
        return new InlineKeyboardMarkup(keyboard);
    }

    public InlineKeyboardMarkup createTaskActionsKeyboard(Document document) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        if (document.getStatus() != DocumentStatus.CANCELLED && document.getStatus() != DocumentStatus.COMPLETED) {
            buttons.add(createButton("🚫 Отменить", "close_without_execution:" + document.getRegNumber()));
        }
        if (document.getStatus() == DocumentStatus.IN_PROGRESS || document.getStatus() == DocumentStatus.PENDING) {
            buttons.add(createButton("✅ Завершить", "task_completed:" + document.getRegNumber()));
            buttons.add(createButton("🔄 На доработку", "send_for_revision:" + document.getRegNumber()));
        }
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        if (!buttons.isEmpty()) {
            keyboard.add(buttons);
        }
        return new InlineKeyboardMarkup(keyboard);
    }
    public InlineKeyboardMarkup createPendingRequestsKeyboard(List<ExtensionRequest> pending) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (ExtensionRequest req : pending) {
            keyboard.add(List.of(
                    createButton("✅ Одобрить №" + req.getDocument().getRegNumber(), "approve_extend:" + req.getId()),
                    createButton("❌ Отклонить №" + req.getDocument().getRegNumber(), "reject_extend:" + req.getId())
            ));
        }
        return new InlineKeyboardMarkup(keyboard);
    }


    public InlineKeyboardMarkup createUserTaskActionsKeyboard(String regNumber) {
        return new InlineKeyboardMarkup(List.of(
                List.of(
                        createButton("📤 Отправить результат", "send_result:" + regNumber),
                        createButton("⏳ Попросить продлить", "request_extend:" + regNumber)
                )
        ));
    }


    public InlineKeyboardMarkup createFileResultKeyboard(String regNumber) {
        return new InlineKeyboardMarkup(List.of(
                List.of(
                        createButton("Поручение исполнено", "task_completed:" + regNumber),
                        createButton("Отправить на доработку", "send_for_revision:" + regNumber)
                )
        ));
    }

    public InlineKeyboardMarkup createDepartmentKeyboard(List<Department> departments, int page) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        int start = page * DEPARTMENTS_PER_PAGE;
        int end = Math.min(start + DEPARTMENTS_PER_PAGE, departments.size());

        for (int i = start; i < end; i++) {
            Department dept = departments.get(i);
            keyboard.add(List.of(createButton(dept.getName(), "dept:" + dept.getId())));
        }

        addPagination(keyboard, page, departments.size(), "page:");
        return new InlineKeyboardMarkup(keyboard);
    }

    public InlineKeyboardMarkup createBranchKeyboard(List<Branch> branches, int page) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        int start = page * BRANCHES_PER_PAGE;
        int end = Math.min(start + BRANCHES_PER_PAGE, branches.size());

        for (int i = start; i < end; i++) {
            Branch branch = branches.get(i);
            keyboard.add(List.of(createButton(branch.getName(), "branch:" + branch.getId())));
        }

        addPagination(keyboard, page, branches.size(), "branchpage:");
        return new InlineKeyboardMarkup(keyboard);
    }

    public InlineKeyboardMarkup createPositionKeyboard(List<Position> positions, int page) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        int start = page * POSITIONS_PER_PAGE;
        int end = Math.min(start + POSITIONS_PER_PAGE, positions.size());

        for (int i = start; i < end; i++) {
            Position pos = positions.get(i);
            keyboard.add(List.of(createButton(pos.getName(), "pos:" + pos.getId())));
        }

        addPagination(keyboard, page, positions.size(), "pospage:");
        return new InlineKeyboardMarkup(keyboard);
    }


    public InlineKeyboardMarkup createBackToStatsKeyboard() {
        return new InlineKeyboardMarkup(List.of(
                List.of(createButton("⬅ Назад к статистике", "tasks_back"))
        ));
    }

    private void addPagination(List<List<InlineKeyboardButton>> keyboard, int page, int total, String prefix) {
        List<InlineKeyboardButton> pagination = new ArrayList<>();
        if (page > 0) {
            pagination.add(createButton("⬅ Назад", prefix + (page - 1)));
        }
        if ((page + 1) * Math.max(DEPARTMENTS_PER_PAGE, Math.max(POSITIONS_PER_PAGE, BRANCHES_PER_PAGE)) < total) {
            pagination.add(createButton("Вперед ➡", prefix + (page + 1)));
        }
        if (!pagination.isEmpty()) {
            keyboard.add(pagination);
        }
    }

    public InlineKeyboardButton createButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    public InlineKeyboardMarkup createPaginationKeyboard(String statusFilter, int page, int total, int pageSize) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопки действий можно добавить сверху, если нужно
        // rows.add(List.of(new InlineKeyboardButton("🔙 Назад к статистике").setCallbackData("back_to_stats")));

        // Навигация по страницам
        List<InlineKeyboardButton> navButtons = new ArrayList<>();
        int totalPages = (int) Math.ceil((double) total / pageSize);

        if (page > 0) {
            navButtons.add(InlineKeyboardButton.builder()
                    .text("⬅ Назад")
                    .callbackData("tasks_filter:" + statusFilter + ":" + (page - 1))
                    .build());
        }

        if (page < totalPages - 1) {
            navButtons.add(InlineKeyboardButton.builder()
                    .text("Вперёд ➡")
                    .callbackData("tasks_filter:" + statusFilter + ":" + (page + 1))
                    .build());
        }

        rows.add(navButtons);

        rows.add(List.of(InlineKeyboardButton.builder()
                .text("📊 Вернуться к статистике")
                .callbackData("tasks_back")
                .build()));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

}