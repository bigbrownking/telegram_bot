package org.info.tg_bot.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.info.tg_bot.model.enums.UserRole;
import org.info.tg_bot.util.EncryptionConverter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(name = "name")
    @Convert(converter = EncryptionConverter.class)
    private String name;

    @Column(name = "telegram_username")
    private String telegramUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "surname")
    @Convert(converter = EncryptionConverter.class)
    private String surname;

    @Column(name = "fathername")
    @Convert(converter = EncryptionConverter.class)
    private String fathername;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    @OneToMany(mappedBy = "executor", fetch = FetchType.EAGER)
    private Set<Document> documents = new HashSet<>();

    @OneToMany(mappedBy = "uploadedBy", cascade = CascadeType.ALL)
    private Set<Document> uploadedDocuments = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper методы
    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }
    public String getFullName() {
        if (surname != null && name != null) {
            String fullName = surname + " " + name;
            if (fathername != null) {
                fullName += " " + fathername;
            }
            return fullName;
        }
        return getDisplayName();
    }

    public String getDisplayName() {
        if (surname != null && name != null) {
            return surname + " " + name.charAt(0) + ".";
        }
        if (telegramUsername != null && !telegramUsername.isBlank()) {
            return "@" + telegramUsername;
        }
        return "User #" + telegramId;
    }
}