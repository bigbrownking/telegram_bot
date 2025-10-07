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

    private final Map<Long, String> registrationSteps = new HashMap<>();
    private final Map<Long, User.UserBuilder> registrationBuilders = new HashMap<>();

    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    public boolean existsByTelegramId(Long telegramId) {
        return userRepository.existsByTelegramId(telegramId);
    }

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
        registrationBuilders.get(telegramId).department(department);
        registrationSteps.put(telegramId, "POSITION");
    }

    public void updateRegistrationPosition(Long telegramId, String position) {
        registrationBuilders.get(telegramId).position(position);
        registrationSteps.put(telegramId, "NAME");
    }

    public void updateRegistrationName(Long telegramId, String name) {
        registrationBuilders.get(telegramId).name(name);
        registrationSteps.put(telegramId, "SURNAME");
    }

    public void updateRegistrationSurname(Long telegramId, String surname) {
        registrationBuilders.get(telegramId).surname(surname);
        registrationSteps.put(telegramId, "FATHERNAME");
    }

    public void completeRegistration(Long telegramId, String fathername) {
        User.UserBuilder builder = registrationBuilders.get(telegramId);
        builder.fathername(fathername);
        User user = builder.build();
        userRepository.save(user);
        registrationSteps.remove(telegramId);
        registrationBuilders.remove(telegramId);
    }
}