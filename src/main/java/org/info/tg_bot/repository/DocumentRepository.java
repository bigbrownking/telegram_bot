package org.info.tg_bot.repository;

import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    @EntityGraph(attributePaths = {"uploadedBy", "executor"})
    Optional<Document> findByRegNumberAndExecutor(String regNumber, User executor);

    Optional<Document> findByRegNumber(String regNumber);
}
