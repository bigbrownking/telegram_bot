package org.info.tg_bot.repository;

import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.ExtensionRequest;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtensionRequestRepository extends JpaRepository<ExtensionRequest, Long> {

    List<ExtensionRequest> findByUser(User user);

    List<ExtensionRequest> findByDocument(Document document);

    List<ExtensionRequest> findByStatus(RequestStatus status);

    Optional<ExtensionRequest> findTopByDocumentOrderByCreatedAtDesc(Document document);

    boolean existsByDocumentAndUserAndStatus(Document document, User user, RequestStatus status);
    @Query("SELECT er FROM ExtensionRequest er WHERE er.user IN :users AND er.status = :status")
    List<ExtensionRequest> findByUserInAndStatus(List<User> users, RequestStatus status);

    @Query("""
       SELECT er FROM ExtensionRequest er
       WHERE er.document.uploadedBy = :admin
         AND er.status = 'PENDING'
         AND er.reviewedBy IS NOT NULL
       ORDER BY er.createdAt DESC
       """)
    List<ExtensionRequest> findPendingByAdmin(User admin);

}
