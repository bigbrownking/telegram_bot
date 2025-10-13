package org.info.tg_bot.service;

import lombok.RequiredArgsConstructor;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.UserRole;
import org.info.tg_bot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // === Регистрация ===
    private final Map<Long, String> registrationSteps = new HashMap<>();
    private final Map<Long, User.UserBuilder> registrationBuilders = new HashMap<>();

    // === Ожидание пользовательских действий ===
    private final Map<Long, Boolean> waitingForTaskNumber = new HashMap<>();
    private final Map<Long, Long> waitingForFile = new HashMap<>();

    // ======================================================
    // ✅ Работа с пользователями
    // ======================================================

    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    public boolean existsByTelegramId(Long telegramId) {
        return userRepository.existsByTelegramId(telegramId);
    }

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

    public void updateRegistrationDepartment(Long telegramId, String department) {
        registrationBuilders.get(telegramId).department(department.trim());
        registrationSteps.put(telegramId, "POSITION");
    }

    public void updateRegistrationPosition(Long telegramId, String position) {
        registrationBuilders.get(telegramId).position(position.trim());
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

    public void completeRegistration(Long telegramId, String fathername) {
        User.UserBuilder builder = registrationBuilders.get(telegramId);
        builder.fathername(fathername.trim());
        User user = builder.build();
        userRepository.save(user);
        registrationSteps.remove(telegramId);
        registrationBuilders.remove(telegramId);
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

}
