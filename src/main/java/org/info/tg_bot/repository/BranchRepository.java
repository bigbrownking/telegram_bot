package org.info.tg_bot.repository;

import org.info.tg_bot.model.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByName(String name);
    @Query("SELECT b FROM Branch b ORDER BY CASE WHEN b.name = 'ПРОПУСТИТЬ' THEN 0 ELSE 1 END, b.name ASC")
    List<Branch> findAllWithSpecialFirst();
}