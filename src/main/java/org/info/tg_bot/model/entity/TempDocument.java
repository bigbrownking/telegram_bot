package org.info.tg_bot.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.info.tg_bot.model.enums.DocumentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "temp_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TempDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_number")
    private String regNumber;

    @Column(name = "short_content")
    private String summary;

    @Column(name = "execution_date")
    private LocalDate executionDate;

    @Column(name = "structural_unit")
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "responsible_executor")
    private User executor;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
