package org.info.tg_bot.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.info.tg_bot.model.enums.UserRole;

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
    private String name;

    @Column(name = "telegram_username")
    private String telegramUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "surname")
    private String surname;

    @Column(name = "fathername")
    private String fathername;

    @Column(name = "department")
    private String department;

    @Column(name = "position")
    private String position;

    @OneToMany(mappedBy = "executor")
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