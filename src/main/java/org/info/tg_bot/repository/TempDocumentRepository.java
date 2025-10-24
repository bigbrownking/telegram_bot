package org.info.tg_bot.repository;

import org.info.tg_bot.model.entity.TempDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TempDocumentRepository extends JpaRepository<TempDocument, Long> {
}
