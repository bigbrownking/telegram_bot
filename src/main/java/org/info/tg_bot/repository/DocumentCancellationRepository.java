package org.info.tg_bot.repository;

import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.DocumentCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentCancellationRepository extends JpaRepository<DocumentCancellation, Long> {
    List<DocumentCancellation> findByDocument(Document document);
    Optional<DocumentCancellation> findTopByDocumentOrderByCreatedAtDesc(Document document);
}
