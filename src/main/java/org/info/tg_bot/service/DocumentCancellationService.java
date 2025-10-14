package org.info.tg_bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.DocumentCancellation;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.CancellationType;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.info.tg_bot.repository.DocumentCancellationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentCancellationService {

    private final DocumentCancellationRepository cancellationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public DocumentCancellation createCancellation(
            Document document,
            User cancelledBy,
            String reason,
            CancellationType type,
            DocumentStatus oldStatus
    ) {

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentRegNumber", document.getRegNumber());
        metadata.put("executorName", document.getExecutor().getFullName());
        metadata.put("executorTelegramId", document.getExecutor().getTelegramId());
        metadata.put("department", document.getDepartment());
        metadata.put("executionDate", document.getExecutionDate() != null ? document.getExecutionDate().toString() : null);

        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.error("Failed to serialize metadata: {}", e.getMessage());
            metadataJson = "{}";
        }

        DocumentCancellation cancellation = DocumentCancellation.builder()
                .document(document)
                .cancelledBy(cancelledBy)
                .cancellationReason(reason)
                .cancellationType(type)
                .notifiedUser(document.getExecutor())
                .oldStatus(oldStatus)
                .metadata(metadataJson)
                .build();

        DocumentCancellation saved = cancellationRepository.save(cancellation);
        log.info("✅ Document cancellation created: id={}, document={}, type={}",
                saved.getId(), document.getRegNumber(), type);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<DocumentCancellation> findByDocument(Document document) {
        return cancellationRepository.findByDocument(document);
    }
}