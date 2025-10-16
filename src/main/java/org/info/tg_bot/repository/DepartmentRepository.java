package org.info.tg_bot.repository;

import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.info.tg_bot.model.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByName(String name);

}
