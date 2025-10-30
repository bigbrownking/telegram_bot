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
    @Query("SELECT u FROM User u WHERE u.department.id = :depId AND u.position.name = 'РУКОВОДИТЕЛЬ ДЕПАРТАМЕНТА' AND u.isActive = true")
    Optional<User> findDepartmentBoss(Long depId);

    @Query("SELECT u FROM User u WHERE u.department.id = :depId AND u.position.name = 'РУКОВОДИТЕЛЬ УПРАВЛЕНИЯ' AND u.isActive = true")
    Optional<User> findUprBoss(Long depId);

    @Query("SELECT u FROM User u WHERE u.position.name = 'ЗАМЕСТИТЕЛЬ ПРЕДСЕДАТЕЛЯ' AND u.isActive = true")
    Optional<User> findVC();
    Optional<User> findByDepartmentIdAndBranchIdAndPositionNameIn(Long departmentId, Long branchId, String... positionNames);
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