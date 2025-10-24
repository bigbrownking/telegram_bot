package org.info.tg_bot.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.info.tg_bot.model.enums.UserRole;
import org.info.tg_bot.util.EncryptionConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_number")
    @Convert(converter = EncryptionConverter.class)
    private String regNumber;

    @Column(name = "short_content")
    @Convert(converter = EncryptionConverter.class)
    private String summary;

    @Column(name = "execution_date")
    private LocalDate executionDate;

    @Column(name = "structural_unit")
    @Convert(converter = EncryptionConverter.class)
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

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "document",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<ExecutionResult> executionResults = new ArrayList<>();
}
