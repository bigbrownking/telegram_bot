package org.info.tg_bot.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.info.tg_bot.model.enums.CancellationType;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.info.tg_bot.util.EncryptionConverter;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_cancellations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by", nullable = false)
    private User cancelledBy;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    @Convert(converter = EncryptionConverter.class)
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_type")
    private CancellationType cancellationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notified_user")
    private User notifiedUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status")
    private DocumentStatus oldStatus;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}