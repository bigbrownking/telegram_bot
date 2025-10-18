package org.info.tg_bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.AuditLog;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.ActionCategory;
import org.info.tg_bot.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AuditLog logAction(
            User user,
            String actionType,
            ActionCategory actionCategory,
            String entityType,
            Long entityId,
            String description,
            Object oldData,
            Object newData,
            String userAgent,
            String telegramUsername,
            boolean success,
            String errorMessage,
            Map<String, Object> metadata
    ) {
        Map<String, Object> meta = (metadata != null) ? metadata : new HashMap<>();

        String oldDataJson = serializeToJson(oldData);
        String newDataJson = serializeToJson(newData);
        String metadataJson = serializeToJson(meta);

        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .actionType(actionType)
                .actionCategory(actionCategory)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .oldData(oldDataJson)
                .newData(newDataJson)
                .metadata(metadataJson)
                .userAgent(userAgent)
                .telegramUsername(telegramUsername)
                .success(success)
                .errorMessage(errorMessage)
                .build();

        return auditLogRepository.save(auditLog);
    }

    private String serializeToJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize object: {}", e.getMessage());
            return "{}";
        }
    }

}
