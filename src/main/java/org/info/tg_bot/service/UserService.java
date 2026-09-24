package org.info.tg_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Branch;
import org.info.tg_bot.model.entity.Department;
import org.info.tg_bot.model.entity.Position;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.UserRole;
import org.info.tg_bot.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final BranchRepository branchRepository;
    private NotificationService notificationService;

    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // === Регистрация ===
    private final Map<Long, String> registrationSteps = new HashMap<>();
    private final Map<Long, User.UserBuilder> registrationBuilders = new HashMap<>();

    // === Ожидание пользовательских действий ===
    private final Map<Long, Boolean> waitingForTaskNumber = new HashMap<>();
    private final Map<Long, Long> waitingForFile = new HashMap<>();
    private final Map<Long, String> pendingCancellations = new HashMap<>();
    private final Map<Long, Boolean> waitingForCancelDocumentNumber = new HashMap<>();
    private final Map<Long, Long> waitingForExtensionReason = new HashMap<>();
    private final Map<Long, Integer> lastMessageIds = new HashMap<>();
    private final Map<Long, Map<String, String>> tempData = new ConcurrentHashMap<>();


    // === Ожидание действий администратора (из AdminCommandHandler) ===
    private final Map<Long, Long> waitingForNewDate = new HashMap<>(); // Для продления срока
    private final Map<Long, String> waitingForRevisionReason = new HashMap<>(); // Для отправки на доработку
    private final Map<Long, String> waitingForCompletionFeedback = new HashMap<>(); // Для обратной связи при завершении

    // ======================================================
    // ✅ Работа с пользователями
    // ======================================================

    public void setLastMessageId(Long telegramId, Integer messageId) {
            lastMessageIds.put(telegramId, messageId);
    }

    public Integer getLastMessageId(Long telegramId) {
        return lastMessageIds.get(telegramId);
    }

    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    public boolean existsByTelegramId(Long telegramId) {
        return userRepository.existsByTelegramId(telegramId);
    }

    public void save(User user) {
        userRepository.save(user);
    }

    // === НОВОЕ: Время начала ожидания ===
    private final Map<Long, Long> waitingTimestamps = new ConcurrentHashMap<>();

    // ======================================================
    // ✅ Регистрация пользователя
    // ======================================================

    public void startRegistration(Long telegramId, String username) {
        registrationSteps.put(telegramId, "DEPARTMENT");
        registrationBuilders.put(telegramId, User.builder()
                .telegramId(telegramId)
                .telegramUsername(username)
                .role(UserRole.USER)
                .isActive(true));
    }

    public String getRegistrationStep(Long telegramId) {
        return registrationSteps.getOrDefault(telegramId, "");
    }

    public void updateRegistrationDepartment(Long telegramId, String departmentInput) {
        Department department = departmentRepository.findByName(departmentInput).orElseThrow();
        registrationBuilders.get(telegramId).department(department);
        registrationSteps.put(telegramId, "BRANCH");
    }

    public void updateRegistrationBranch(Long telegramId, String branchInput) {
        User.UserBuilder builder = registrationBuilders.get(telegramId);
        if (branchInput == null || branchInput.equals("—")) {
            builder.branch(null);
        } else {
            Branch branch = branchRepository.findByName(branchInput)
                    .orElseThrow(() -> new RuntimeException("Branch not found: " + branchInput));
            builder.branch(branch);
        }
        registrationSteps.put(telegramId, "POSITION");
    }

    public void updateRegistrationPosition(Long telegramId, String positionInput) {
        Position position = positionRepository.findByName(positionInput).orElseThrow();
        registrationBuilders.get(telegramId).position(position);
        registrationSteps.put(telegramId, "NAME");
    }

    public void updateRegistrationName(Long telegramId, String name) {
        registrationBuilders.get(telegramId).name(name.trim());
        registrationSteps.put(telegramId, "SURNAME");
    }

    public void updateRegistrationSurname(Long telegramId, String surname) {
        registrationBuilders.get(telegramId).surname(surname.trim());
        registrationSteps.put(telegramId, "FATHERNAME");
    }

    public void completeRegistration(Long telegramId, String fatherName) {
        User.UserBuilder builder = registrationBuilders.get(telegramId);
        if (builder == null) {
            throw new IllegalStateException("No registration data found for telegramId: " + telegramId);
        }

        if (fatherName != null && !fatherName.trim().isEmpty()) {
            builder.fathername(fatherName.trim());
        } else {
            builder.fathername(null);
        }

        User user = builder.build();
        userRepository.save(user);

        registrationSteps.remove(telegramId);
        registrationBuilders.remove(telegramId);

        log.info("✅ User registered: {} (telegramId: {})", user.getFullName(), telegramId);
    }

    public void setTempData(Long telegramId, String key, String value) {
        tempData.computeIfAbsent(telegramId, k -> new HashMap<>()).put(key, value);
    }

    public String getTempData(Long telegramId, String key) {
        Map<String, String> userData = tempData.get(telegramId);
        return userData != null ? userData.get(key) : null;
    }

    public void clearTempData(Long telegramId, String key) {
        Map<String, String> userData = tempData.get(telegramId);
        if (userData != null) {
            userData.remove(key);
            if (userData.isEmpty()) {
                tempData.remove(telegramId);
            }
        }
    }
    // ======================================================
    // ✅ Ожидание номера поручения
    // ======================================================

    public void setWaitingForTaskNumber(Long telegramId, boolean waiting) {
        if (waiting) waitingForTaskNumber.put(telegramId, true);
        else waitingForTaskNumber.remove(telegramId);
    }

    public boolean isWaitingForTaskNumber(Long telegramId) {
        return waitingForTaskNumber.getOrDefault(telegramId, false);
    }

    public void clearWaitingForTaskNumber(Long telegramId) {
        waitingForTaskNumber.remove(telegramId);
    }

    // ======================================================
    // ✅ Ожидание файла от пользователя
    // ======================================================

    public void setWaitingForFile(Long telegramId, Long documentId) {
        waitingForFile.put(telegramId, documentId);
    }

    public boolean isWaitingForFile(Long telegramId) {
        return waitingForFile.containsKey(telegramId);
    }

    public Optional<Long> getWaitingFileDocId(Long telegramId) {
        return Optional.ofNullable(waitingForFile.get(telegramId));
    }

    public void clearWaitingForFile(Long telegramId) {
        waitingForFile.remove(telegramId);
    }

    public Long getDocumentIdWaitingForFile(Long telegramId) {
        return waitingForFile.get(telegramId);
    }

    // ======================================================
    // ✅ Ожидание номера документа для отмены
    // ======================================================

    public void setWaitingForCancelDocumentNumber(Long telegramId, boolean waiting) {
        if (waiting) waitingForCancelDocumentNumber.put(telegramId, true);
        else waitingForCancelDocumentNumber.remove(telegramId);
    }

    public boolean isWaitingForCancelDocumentNumber(Long telegramId) {
        return waitingForCancelDocumentNumber.getOrDefault(telegramId, false);
    }

    public void clearWaitingForCancelDocumentNumber(Long telegramId) {
        waitingForCancelDocumentNumber.remove(telegramId);
    }

    // ======================================================
    // ✅ Ожидание причины отмены
    // ======================================================

    public void setPendingCancellation(Long telegramId, String regNumber, String typeName) {
        pendingCancellations.put(telegramId, regNumber + ":" + typeName);
    }

    public boolean isWaitingForCancellationReason(Long telegramId) {
        return pendingCancellations.containsKey(telegramId);
    }

    public Optional<String> consumePendingCancellation(Long telegramId) {
        return Optional.ofNullable(pendingCancellations.remove(telegramId));
    }

    public void clearPendingCancellation(Long telegramId) {
        pendingCancellations.remove(telegramId);
    }

    // ======================================================
    // ✅ Ожидание новой даты для продления (из AdminCommandHandler)
    // ======================================================

    public void setWaitingForNewDate(Long adminTelegramId, Long requestId) {
        waitingForNewDate.put(adminTelegramId, requestId);
    }

    public boolean isWaitingForNewDate(Long adminTelegramId) {
        return waitingForNewDate.containsKey(adminTelegramId);
    }

    public Optional<Long> consumeWaitingForNewDate(Long adminTelegramId) {
        return Optional.ofNullable(waitingForNewDate.remove(adminTelegramId));
    }

    public void clearWaitingForNewDate(Long adminTelegramId) {
        waitingForNewDate.remove(adminTelegramId);
    }

    // ======================================================
    // ✅ Ожидание причины доработки (из AdminCommandHandler)
    // ======================================================

    public void setWaitingForRevisionReason(Long adminTelegramId, String regNumber) {
        waitingForRevisionReason.put(adminTelegramId, regNumber);
    }

    public boolean isWaitingForRevisionReason(Long adminTelegramId) {
        return waitingForRevisionReason.containsKey(adminTelegramId);
    }

    public Optional<String> consumeWaitingForRevisionReason(Long adminTelegramId) {
        return Optional.ofNullable(waitingForRevisionReason.remove(adminTelegramId));
    }

    public void clearWaitingForRevisionReason(Long adminTelegramId) {
        waitingForRevisionReason.remove(adminTelegramId);
    }

    public void setWaitingForCompletionFeedback(Long adminTelegramId, String regNumber) {
        waitingForCompletionFeedback.put(adminTelegramId, regNumber);
    }

    public boolean isWaitingForCompletionFeedback(Long adminTelegramId) {
        return waitingForCompletionFeedback.containsKey(adminTelegramId);
    }

    public Optional<String> consumeWaitingForCompletionFeedback(Long adminTelegramId) {
        return Optional.ofNullable(waitingForCompletionFeedback.remove(adminTelegramId));
    }

    public void clearWaitingForCompletionFeedback(Long adminTelegramId) {
        waitingForCompletionFeedback.remove(adminTelegramId);
    }

    public void setWaitingForExtensionReason(Long telegramId, Long documentId) {
        waitingForExtensionReason.put(telegramId, documentId);
    }

    public boolean isWaitingForExtensionReason(Long telegramId) {
        return waitingForExtensionReason.containsKey(telegramId);
    }

    public Optional<Long> consumeWaitingForExtensionReason(Long telegramId) {
        return Optional.ofNullable(waitingForExtensionReason.remove(telegramId));
    }

    public void clearWaitingForExtensionReason(Long telegramId) {
        waitingForExtensionReason.remove(telegramId);
    }

    public void clearStaleWaits(long timeoutMs) {
        long now = System.currentTimeMillis();
        waitingTimestamps.entrySet().removeIf(entry -> {
            Long telegramId = entry.getKey();
            Long timestamp = entry.getValue();

            if (now - timestamp > timeoutMs) {
                log.info("Clearing stale state for user telegramId={}", telegramId);

                // Сбрасываем все возможные ожидания
                clearWaitingForTaskNumber(telegramId);
                clearWaitingForFile(telegramId);
                clearWaitingForCancelDocumentNumber(telegramId);
                clearPendingCancellation(telegramId);
                clearWaitingForNewDate(telegramId);
                clearWaitingForRevisionReason(telegramId);
                clearWaitingForCompletionFeedback(telegramId);
                clearWaitingForExtensionReason(telegramId);

                // Удаляем сообщение, если оно ещё висит
                Integer msgId = getLastMessageId(telegramId);
                if (msgId != null) {
                    setLastMessageId(telegramId, null);
                }

                return true; // Удаляем из timestamps
            }
            return false;
        });
    }
    public User getVC(){
        return userRepository.findVC().orElse(null);
    }
    public User findBoss(Long telegramId) {
        Optional<User> optUser = userRepository.findByTelegramId(telegramId);
        if (optUser.isEmpty()) {
            log.warn("No user found for telegramId: {}", telegramId);
            return null;
        }

        User user = optUser.get();
        String positionName = user.getPosition().getName();
        Long departmentId = user.getDepartment().getId();
        Branch branch = user.getBranch();
        Long branchId = (branch != null) ? branch.getId() : null;

        User boss;

        if ("РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ".equalsIgnoreCase(positionName) || "РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ А".equalsIgnoreCase(positionName)) {
            boss = userRepository.findDepartmentBoss(departmentId).orElse(null);

        } else if ("РУКОВОДИТЕЛЬ ДЕПАРТАМЕНТА".equalsIgnoreCase(positionName)) {
            boss = userRepository.findVC().orElse(null);
        } else {
            if (branchId != null) {
                boss = userRepository.findByDepartmentIdAndBranchIdAndPositionNameIn(
                        departmentId,
                        branchId,
                        "РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ",
                        "РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ А"
                ).orElse(null);
                if (boss == null) {
                    boss = userRepository.findDepartmentBoss(departmentId).orElse(null);
                }
            } else {
                boss = userRepository.findUprBoss(departmentId).orElse(null);
                if (boss == null) {
                    boss = userRepository.findDepartmentBoss(departmentId).orElse(null);
                }
            }
        }

        log.info("Boss for user {} (telegramId: {}) is {} (telegramId: {})",
                user.getFullName(),
                telegramId,
                boss != null ? boss.getFullName() : "null",
                boss != null ? boss.getTelegramId() : "null");

        return boss;
    }


    public boolean isBoss(Long telegramId) {
        Optional<User> optUser = userRepository.findByTelegramId(telegramId);
        if (optUser.isEmpty()) {
            log.warn("No user found for telegramId: {}", telegramId);
            return false;
        }
        User user = optUser.get();
        String positionName = user.getPosition().getName();
        return positionName.equalsIgnoreCase("РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ") ||
                positionName.equalsIgnoreCase("РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ А") ||
                positionName.equalsIgnoreCase("РУКОВОДИТЕЛЬ ДЕПАРТАМЕНТА") ||
                positionName.equalsIgnoreCase("ЗАМЕСТИТЕЛЬ ПРЕДСЕДАТЕЛЯ");
    }

    public List<User> findSupervisedUsers(Long bossTelegramId) {
        Optional<User> bossOpt = userRepository.findByTelegramId(bossTelegramId);
        if (bossOpt.isEmpty()) {
            log.warn("No boss found for telegramId: {}", bossTelegramId);
            return List.of();
        }

        User boss = bossOpt.get();

        String positionName = boss.getPosition().getName();
        Long departmentId = boss.getDepartment().getId();
        Branch branch = boss.getBranch();
        Long branchId = (branch != null) ? branch.getId() : null;

        List<User> supervisedUsers = List.of();

        if ("РУКОВОДИТЕЛЬ ДЕПАРТАМЕНТА".equalsIgnoreCase(positionName)) {
            supervisedUsers = userRepository.findSupervisedUsersByDepartment(departmentId);
            log.info("Found {} supervised users in department {} for boss {} (telegramId: {})",
                    supervisedUsers.size(), departmentId, boss.getFullName(), bossTelegramId);

        } else if ("РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ".equalsIgnoreCase(positionName) ||
                "РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ А".equalsIgnoreCase(positionName)) {

            if (branchId != null) {
                supervisedUsers = userRepository.findSupervisedUsersByDepartmentAndBranch(departmentId, branchId);
                log.info("Found {} supervised users in branch {} for boss {} (telegramId: {})",
                        supervisedUsers.size(), branchId, boss.getFullName(), bossTelegramId);
            } else {
                supervisedUsers = userRepository.findSupervisedUsersByDepartment(departmentId);
                log.info("Branch is null, returning supervised users only by department {}", departmentId);
            }
        } else {
            log.warn("Unsupported boss position {} for telegramId: {}", positionName, bossTelegramId);
        }

        return supervisedUsers;
    }

    public String getAllUsernames() {
        return userRepository.findAll().stream()
                .map(user -> String.format("%d | %s", user.getId(), user.getFullName()))
                .filter(line -> !line.endsWith(" | "))
                .collect(java.util.stream.Collectors.joining("\n"));
    }
    @Transactional
    public String deleteUserByName(String name, String surname, String fathername) {
        Optional<User> userOpt = userRepository.findByFullName(name, surname, fathername);
        if (userOpt.isEmpty()) {
            return "User not found.";
        }

        User user = userOpt.get();
        Long telegramId = user.getTelegramId();

        String fullName = user.getFullName();

        String text = String.format("""
        Уважаемый(ая) <b>%s</b>!

        Ваша регистрация была удалена администратором.
        Причина: некорректные данные.
        Пожалуйста, начните регистрацию заново с помощью /start.
        """, fullName);

        auditLogRepository.deleteByUserId(user.getId());
        userRepository.delete(user);

        notificationService.notifyUser(telegramId, text, null);




        boolean deleted = userRepository.findByFullName(name, surname, fathername).isEmpty();
        return deleted
                ? "User deleted (notification attempted)."
                : "User deletion verification failed.";
    }

}