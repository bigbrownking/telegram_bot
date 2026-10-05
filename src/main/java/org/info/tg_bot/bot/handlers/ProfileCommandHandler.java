package org.info.tg_bot.bot.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Branch;
import org.info.tg_bot.model.entity.Department;
import org.info.tg_bot.model.entity.Position;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.ActionCategory;
import org.info.tg_bot.repository.BranchRepository;
import org.info.tg_bot.repository.DepartmentRepository;
import org.info.tg_bot.repository.PositionRepository;
import org.info.tg_bot.service.AuditLogService;
import org.info.tg_bot.service.UserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileCommandHandler {

    public static final String CB_PREFIX = "prof:";

    private static final int PAGE_SIZE = 8;
    private static final int MAX_NAME_LENGTH = 50;
    private static final String SKIP_BRANCH_NAME = "ПРОПУСТИТЬ";
    private static final String LETTERS = "А-ЯЁӘҒҚҢӨҰҮҺІа-яёәғқңөұүһі";
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[" + LETTERS + "]+(-[" + LETTERS + "]+)*$");
    private static final Set<String> MANAGER_POSITIONS = Set.of(
            "РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ",
            "РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ А",
            "РУКОВОДИТЕЛЬ ДЕПАРТАМЕНТА",
            "ЗАМЕСТИТЕЛЬ ПРЕДСЕДАТЕЛЯ"
    );

    private static final String F_NAME = "name";
    private static final String F_SURNAME = "surname";
    private static final String F_FATHER = "father";
    private static final String F_DEPT = "dept";
    private static final String F_BRANCH = "branch";
    private static final String F_POS = "pos";

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;
    private final PositionRepository positionRepository;

    private record Option(Long id, String name) {}

    // ======================================================
    // /profile
    // ======================================================

    public SendMessage handleProfileCommand(Long chatId, Long telegramId) {
        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            return notRegistered(chatId);
        }

        userService.clearWaitingForTaskNumber(telegramId);
        userService.clearWaitingForFile(telegramId);
        userService.clearWaitingForExtensionReason(telegramId);
        userService.clearProfileEditField(telegramId);

        return profileCard(chatId, userOpt.get(), null);
    }

    // ======================================================
    // Callback-кнопки prof:*
    // ======================================================

    public SendMessage handleCallback(Long chatId, Long telegramId, String data) {
        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            return notRegistered(chatId);
        }
        User user = userOpt.get();

        String[] p = data.substring(CB_PREFIX.length()).split(":");

        try {
            return switch (p[0]) {
                case "show" -> {
                    userService.clearProfileEditField(telegramId);
                    yield profileCard(chatId, user, null);
                }
                case "close" -> {
                    userService.clearProfileEditField(telegramId);
                    yield new SendMessage(chatId.toString(), "Профиль закрыт. Открыть снова: /profile");
                }
                case "edit" -> startEdit(chatId, telegramId, user, p[1]);
                case "page" -> isOrgField(p[1]) && isManager(user)
                        ? managerLocked(chatId, user)
                        : listPage(chatId, user, p[1], Integer.parseInt(p[2]), null);
                case "set" -> isManager(user)
                        ? managerLocked(chatId, user)
                        : applySelection(chatId, user, p[1], p[2]);
                case "delfather" -> {
                    userService.clearProfileEditField(telegramId);
                    user.setFathername(null);
                    userService.save(user);
                    audit(user, F_FATHER, null);
                    yield profileCard(chatId, user, "✅ Отчество удалено.");
                }
                default -> profileCard(chatId, user, null);
            };
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            log.warn("Bad profile callback '{}' from telegramId={}", data, telegramId);
            return profileCard(chatId, user, "⚠ Не удалось обработать действие.");
        }
    }

    // ======================================================
    // Текстовый ввод (имя / фамилия / отчество)
    // ======================================================

    public SendMessage handleTextInput(Long chatId, Long telegramId, String input) {
        String field = userService.getProfileEditField(telegramId);
        Optional<User> userOpt = userService.findByTelegramId(telegramId);

        if (field == null || userOpt.isEmpty()) {
            userService.clearProfileEditField(telegramId);
            return new SendMessage(chatId.toString(), "⚠ Редактирование не активно. Используйте /profile.");
        }

        User user = userOpt.get();
        String value = input == null ? "" : input.strip();

        if (value.length() > MAX_NAME_LENGTH || !NAME_PATTERN.matcher(value).matches()) {
            SendMessage retry = textPrompt(chatId, user, field);
            retry.setText("⚠ Нужно одно слово кириллицей (допускается дефис), например: <i>Иванов</i> или <i>Ахмет-Али</i>.\n\n"
                    + retry.getText());
            return retry;
        }

        switch (field) {
            case F_NAME -> user.setName(value);
            case F_SURNAME -> user.setSurname(value);
            case F_FATHER -> user.setFathername(value);
            default -> {
                userService.clearProfileEditField(telegramId);
                return profileCard(chatId, user, null);
            }
        }

        userService.save(user);
        userService.clearProfileEditField(telegramId);
        audit(user, field, null);

        return profileCard(chatId, user, "✅ Изменения сохранены.");
    }

    // ======================================================
    // Внутренняя логика
    // ======================================================

    private SendMessage startEdit(Long chatId, Long telegramId, User user, String field) {
        return switch (field) {
            case F_NAME, F_SURNAME, F_FATHER -> {
                userService.setProfileEditField(telegramId, field);
                yield textPrompt(chatId, user, field);
            }
            case F_DEPT, F_BRANCH, F_POS -> {
                userService.clearProfileEditField(telegramId);
                yield isManager(user) ? managerLocked(chatId, user) : listPage(chatId, user, field, 0, null);
            }
            default -> profileCard(chatId, user, null);
        };
    }

    private SendMessage applySelection(Long chatId, User user, String field, String value) {
        switch (field) {
            case F_DEPT -> {
                Department dept = departmentRepository.findById(Long.parseLong(value)).orElse(null);
                if (dept == null) {
                    return listPage(chatId, user, F_DEPT, 0, "⚠ Отдел не найден.");
                }
                Long oldId = idOf(user.getDepartment());
                if (Objects.equals(oldId, dept.getId())) {
                    return profileCard(chatId, user, "Отдел не изменился.");
                }
                user.setDepartment(dept);
                userService.save(user);
                audit(user, F_DEPT, changeMeta(oldId, dept.getId()));

                return listPage(chatId, user, F_BRANCH, 0,
                        "✅ Отдел изменён на <b>" + esc(dept.getName()) + "</b>.\n"
                                + "Проверьте управление: выберите новое или вернитесь к профилю.");
            }
            case F_BRANCH -> {
                Branch branch = null;
                if (!"none".equals(value)) {
                    branch = branchRepository.findById(Long.parseLong(value)).orElse(null);
                    if (branch == null) {
                        return listPage(chatId, user, F_BRANCH, 0, "⚠ Управление не найдено.");
                    }
                    if (SKIP_BRANCH_NAME.equals(branch.getName())) {
                        branch = null;
                    }
                }
                Long oldId = idOf(user.getBranch());
                Long newId = idOf(branch);
                if (!Objects.equals(oldId, newId)) {
                    user.setBranch(branch);
                    userService.save(user);
                    audit(user, F_BRANCH, changeMeta(oldId, newId));
                }
                return profileCard(chatId, user, "✅ Управление обновлено.");
            }
            case F_POS -> {
                Position pos = positionRepository.findById(Long.parseLong(value)).orElse(null);
                if (pos == null) {
                    return listPage(chatId, user, F_POS, 0, "⚠ Должность не найдена.");
                }
                if (MANAGER_POSITIONS.contains(pos.getName())) {
                    return profileCard(chatId, user,
                            "🔒 Руководящую должность может назначить только администратор.");
                }
                Long oldId = idOf(user.getPosition());
                if (!Objects.equals(oldId, pos.getId())) {
                    user.setPosition(pos);
                    userService.save(user);
                    audit(user, F_POS, changeMeta(oldId, pos.getId()));
                }
                return profileCard(chatId, user, "✅ Должность обновлена.");
            }
            default -> {
                return profileCard(chatId, user, null);
            }
        }
    }

    // ======================================================
    // Сообщения и клавиатуры
    // ======================================================

    private SendMessage profileCard(Long chatId, User user, String notice) {
        boolean manager = isManager(user);

        StringBuilder sb = new StringBuilder();
        if (notice != null) {
            sb.append(notice).append("\n\n");
        }
        sb.append("👤 <b>Мой профиль</b>\n\n")
                .append("Фамилия: <b>").append(orDash(user.getSurname())).append("</b>\n")
                .append("Имя: <b>").append(orDash(user.getName())).append("</b>\n")
                .append("Отчество: <b>").append(orDash(user.getFathername())).append("</b>\n\n")
                .append("🏢 Отдел: ").append(orDash(nameOf(user.getDepartment()))).append("\n")
                .append("🏛 Управление: ").append(orDash(nameOf(user.getBranch()))).append("\n")
                .append("💼 Должность: ").append(orDash(nameOf(user.getPosition()))).append("\n");

        if (manager) {
            sb.append("\n🔒 <i>Отдел, управление и должность руководителя меняет администратор.</i>\n");
        }
        sb.append("\nЧто хотите изменить?");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("✏️ Фамилия", CB_PREFIX + "edit:" + F_SURNAME),
                button("✏️ Имя", CB_PREFIX + "edit:" + F_NAME),
                button("✏️ Отчество", CB_PREFIX + "edit:" + F_FATHER)
        ));
        if (!manager) {
            rows.add(List.of(
                    button("🏢 Отдел", CB_PREFIX + "edit:" + F_DEPT),
                    button("🏛 Управление", CB_PREFIX + "edit:" + F_BRANCH)
            ));
            rows.add(List.of(button("💼 Должность", CB_PREFIX + "edit:" + F_POS)));
        }
        rows.add(List.of(button("✖️ Закрыть", CB_PREFIX + "close")));

        return html(chatId, sb.toString(), rows);
    }

    private SendMessage textPrompt(Long chatId, User user, String field) {
        String current = switch (field) {
            case F_NAME -> user.getName();
            case F_SURNAME -> user.getSurname();
            default -> user.getFathername();
        };

        String text = "✏️ <b>" + fieldTitle(field) + "</b>\n\n"
                + "Сейчас: <b>" + orDash(current) + "</b>\n\n"
                + "Отправьте новое значение одним сообщением — как в удостоверении, кириллицей, одним словом.";

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (F_FATHER.equals(field) && current != null) {
            rows.add(List.of(button("🗑 Удалить отчество", CB_PREFIX + "delfather")));
        }
        rows.add(List.of(button("⬅️ Отмена", CB_PREFIX + "show")));

        return html(chatId, text, rows);
    }

    private SendMessage listPage(Long chatId, User user, String field, int page, String header) {
        List<Option> options = switch (field) {
            case F_DEPT -> departmentRepository.findAll().stream()
                    .map(d -> new Option(d.getId(), d.getName()))
                    .toList();
            case F_BRANCH -> branchRepository.findAllWithSpecialFirst().stream()
                    .filter(b -> !SKIP_BRANCH_NAME.equals(b.getName()))
                    .map(b -> new Option(b.getId(), b.getName()))
                    .toList();
            case F_POS -> positionRepository.findAll().stream()
                    .filter(p -> !MANAGER_POSITIONS.contains(p.getName()))
                    .map(p -> new Option(p.getId(), p.getName()))
                    .toList();
            default -> List.of();
        };

        Long currentId = switch (field) {
            case F_DEPT -> idOf(user.getDepartment());
            case F_BRANCH -> idOf(user.getBranch());
            case F_POS -> idOf(user.getPosition());
            default -> null;
        };

        int totalPages = Math.max(1, (options.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, options.size());

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (F_BRANCH.equals(field) && safePage == 0) {
            rows.add(List.of(button((currentId == null ? "✅ " : "") + "— Без управления —",
                    CB_PREFIX + "set:" + F_BRANCH + ":none")));
        }

        for (Option o : options.subList(from, to)) {
            String label = (Objects.equals(o.id(), currentId) ? "✅ " : "") + o.name();
            rows.add(List.of(button(label, CB_PREFIX + "set:" + field + ":" + o.id())));
        }

        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (safePage > 0) {
            nav.add(button("◀️ Назад", CB_PREFIX + "page:" + field + ":" + (safePage - 1)));
        }
        if (safePage < totalPages - 1) {
            nav.add(button("Вперёд ▶️", CB_PREFIX + "page:" + field + ":" + (safePage + 1)));
        }
        if (!nav.isEmpty()) {
            rows.add(nav);
        }
        rows.add(List.of(button("⬅️ К профилю", CB_PREFIX + "show")));

        String text = (header != null ? header + "\n\n" : "")
                + "Выберите <b>" + fieldTitle(field).toLowerCase() + "</b>"
                + (totalPages > 1 ? " (стр. " + (safePage + 1) + " из " + totalPages + ")" : "")
                + ":\n<i>✅ — текущее значение</i>";

        return html(chatId, text, rows);
    }

    private SendMessage managerLocked(Long chatId, User user) {
        return profileCard(chatId, user,
                "🔒 Отдел, управление и должность руководителя меняет администратор.");
    }

    // ======================================================
    // Вспомогательные методы
    // ======================================================

    private boolean isManager(User user) {
        return user.getPosition() != null && MANAGER_POSITIONS.contains(user.getPosition().getName());
    }

    private boolean isOrgField(String field) {
        return F_DEPT.equals(field) || F_BRANCH.equals(field) || F_POS.equals(field);
    }

    private void audit(User user, String field, Map<String, Object> extra) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("field", field);
        if (extra != null) {
            meta.putAll(extra);
        }
        try {
            auditLogService.logAction(
                    user,
                    "UPDATE_PROFILE",
                    ActionCategory.USER,
                    "User",
                    user.getId(),
                    "User updated profile field: " + field,
                    null,
                    null,
                    "Telegram Bot",
                    user.getTelegramUsername(),
                    true,
                    null,
                    meta
            );
        } catch (Exception e) {
            log.warn("Failed to write audit log for profile update: {}", e.getMessage());
        }
    }

    private Map<String, Object> changeMeta(Long oldId, Long newId) {
        Map<String, Object> m = new HashMap<>();
        m.put("oldId", oldId);
        m.put("newId", newId);
        return m;
    }

    private String fieldTitle(String field) {
        return switch (field) {
            case F_NAME -> "Имя";
            case F_SURNAME -> "Фамилия";
            case F_FATHER -> "Отчество";
            case F_DEPT -> "Отдел";
            case F_BRANCH -> "Управление";
            case F_POS -> "Должность";
            default -> field;
        };
    }

    private static Long idOf(Department d) { return d != null ? d.getId() : null; }
    private static Long idOf(Branch b) { return b != null ? b.getId() : null; }
    private static Long idOf(Position p) { return p != null ? p.getId() : null; }

    private static String nameOf(Department d) { return d != null ? d.getName() : null; }
    private static String nameOf(Branch b) {
        return (b == null || SKIP_BRANCH_NAME.equals(b.getName())) ? null : b.getName();
    }
    private static String nameOf(Position p) { return p != null ? p.getName() : null; }

    private static String orDash(String s) {
        return (s == null || s.isBlank()) ? "—" : esc(s);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    private static SendMessage html(Long chatId, String text, List<List<InlineKeyboardButton>> rows) {
        SendMessage msg = new SendMessage(chatId.toString(), text);
        msg.enableHtml(true);
        msg.setReplyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build());
        return msg;
    }

    private static SendMessage notRegistered(Long chatId) {
        return new SendMessage(chatId.toString(), "❌ Вы не зарегистрированы. Используйте /start.");
    }
}