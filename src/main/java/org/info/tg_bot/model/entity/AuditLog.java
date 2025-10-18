package org.info.tg_bot.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.info.tg_bot.model.enums.ActionCategory;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_category", length = 50)
    private ActionCategory actionCategory;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "old_data", columnDefinition = "JSONB")
    private String oldData;

    @Column(name = "new_data", columnDefinition = "JSONB")
    private String newData;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "telegram_username")
    private String telegramUsername;

    @Column(name = "success")
    private Boolean success = true;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}