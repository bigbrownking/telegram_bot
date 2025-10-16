package org.info.tg_bot.repository;

import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    @EntityGraph(attributePaths = {"uploadedBy", "executor"})
    Optional<Document> findByRegNumberAndExecutor(String regNumber, User executor);
    @Query("SELECT d FROM Document d " +
            "LEFT JOIN FETCH d.uploadedBy " +
            "LEFT JOIN FETCH d.executor " +
            "WHERE d.executor = :executor " +
            "AND d.status NOT IN :statuses")
    List<Document> findActiveDocumentsByExecutor(User executor, List<DocumentStatus> statuses);


    @Query("SELECT d FROM Document d " +
            "LEFT JOIN FETCH d.uploadedBy " +
            "LEFT JOIN FETCH d.executor " +
            "WHERE d.executionDate < :today " +
            "AND d.status NOT IN :statuses")
    List<Document> findByExecutionDateBeforeAndStatusNotIn(LocalDate today, List<DocumentStatus> statuses);

    Optional<Document> findByRegNumber(String regNumber);

    @EntityGraph(attributePaths = {"uploadedBy", "executor"})
    List<Document> findByUploadedByOrderByCreatedAtDesc(User uploadedBy);

    @EntityGraph(attributePaths = {"uploadedBy", "executor"})
    List<Document> findByUploadedByAndStatusOrderByCreatedAtDesc(User uploadedBy, DocumentStatus status);

}
