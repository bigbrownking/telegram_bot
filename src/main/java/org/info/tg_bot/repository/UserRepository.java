package org.info.tg_bot.repository;

import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTelegramId(Long telegramId);
    List<User> findBySurnameIgnoreCase(String surname);
    List<User> findByRole(UserRole role);
    List<User> findByIsActiveTrue();
    boolean existsByTelegramId(Long telegramId);
}