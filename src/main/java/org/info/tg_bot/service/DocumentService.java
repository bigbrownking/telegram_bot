package org.info.tg_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
