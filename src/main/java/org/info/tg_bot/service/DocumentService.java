package org.info.tg_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.info.tg_bot.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public Optional<Document> findByRegNumber(String regNumber) {
        return documentRepository.findByRegNumber(regNumber);
    }

    public Optional<Document> findById(long id){
        return documentRepository.findById(id);
    }

    public Optional<Document> findByRegNumberAndExecutor(String regNumber, User executor) {
        return documentRepository.findByRegNumberAndExecutor(regNumber, executor);
    }
    public List<Document> findAllByAuthor(User uploadedBy) {
        return documentRepository.findAll().stream()
                .filter(d -> d.getUploadedBy() != null && d.getUploadedBy().equals(uploadedBy))
                .toList();
    }
    public Document save(Document document) {
        return documentRepository.save(document);
    }

    public void delete(Long id) {
        if (documentRepository.existsById(id)) {
            documentRepository.deleteById(id);
            log.info("🗑️ Document {} deleted", id);
        }
    }
    @Transactional(readOnly = true)
    public List<Document> findActiveDocumentsByExecutor(User executor) {
        return documentRepository.findActiveDocumentsByExecutor(executor,
                List.of(DocumentStatus.CANCELLED, DocumentStatus.COMPLETED));
    }

    @Transactional(readOnly = true)
    public List<Document> findActiveDocumentsNotCompletedOrCancelled(LocalDate today) {
        return documentRepository.findByExecutionDateBeforeAndStatusNotIn(today,
                List.of(DocumentStatus.COMPLETED, DocumentStatus.CANCELLED));
    }
    @Transactional(readOnly = true)
    public List<Document> findByUploadedBy(User uploadedBy) {
        return documentRepository.findByUploadedByOrderByCreatedAtDesc(uploadedBy);
    }

    @Transactional(readOnly = true)
    public List<Document> findByUploadedByAndStatus(User uploadedBy, DocumentStatus status) {
        return documentRepository.findByUploadedByAndStatusOrderByCreatedAtDesc(uploadedBy, status);
    }
}
