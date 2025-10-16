package org.info.tg_bot.repository;

import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.ExecutionResult;
import org.info.tg_bot.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExecutionResultRepository extends JpaRepository<ExecutionResult, Long> {

    List<ExecutionResult> findAllByDocumentOrderBySubmittedAtDesc(Document document);

    List<ExecutionResult> findAllByDocumentAndUser(Document document, User user);

    Optional<ExecutionResult> findFirstByDocumentAndUserOrderBySubmittedAtDesc(Document document, User user);

    int countByDocumentAndUser(Document document, User user);
}
