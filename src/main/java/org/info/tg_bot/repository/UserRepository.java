package org.info.tg_bot.repository;

import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTelegramId(Long telegramId);
    List<User> findBySurnameIgnoreCase(String surname);
    List<User> findByRole(UserRole role);
    List<User> findByIsActiveTrue();
    Optional<User> findByDepartmentIdAndPositionName(Long depId, String positionName);
    Optional<User> findByBranchIdAndPositionNameIn(Long branchId, String... positionNames);
    boolean existsByTelegramId(Long telegramId);
    @Query("SELECT u FROM User u WHERE (u.department.id = :departmentId OR u.branch.id = :branchId) " +
            "AND u.position.name NOT IN ('РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ', 'РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ А', 'РУКОВОДИТЕЛЬ ДЕПАРТАМЕНТА') " +
            "AND u.isActive = true")
    List<User> findSupervisedUsersByDepartmentAndBranch(Long departmentId, Long branchId);

    @Query("SELECT u FROM User u WHERE u.department.id = :departmentId " +
            "AND u.position.name NOT IN ('РУКОВОДИТЕЛЬ ДЕПАРТАМЕНТА') " +
            "AND u.isActive = true")
    List<User> findSupervisedUsersByDepartment(Long departmentId);
}