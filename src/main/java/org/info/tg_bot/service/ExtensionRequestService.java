package org.info.tg_bot.service;

import jakarta.ws.rs.core.Request;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.ExtensionRequest;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.RequestStatus;
import org.info.tg_bot.repository.ExtensionRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtensionRequestService {

    private final ExtensionRequestRepository extensionRequestRepository;

    @Transactional
    public ExtensionRequest createRequest(Document document, User user, LocalDateTime requestedDate, String reason) {
        if (extensionRequestRepository.existsByDocumentAndUserAndStatus(document, user, RequestStatus.PENDING)) {
            throw new IllegalStateException("⚠ У вас уже есть активный запрос на продление по этому документу");
        }

        ExtensionRequest request = ExtensionRequest.builder()
                .document(document)
                .user(user)
                .requestedDate(requestedDate)
                .reason(reason)
                .status(RequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        log.info("📩 Created new extension request for doc={}, user={}", document.getRegNumber(), user.getTelegramId());
        return extensionRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public Optional<ExtensionRequest> findById(Long id) {
        return extensionRequestRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ExtensionRequest> findPendingRequests() {
        return extensionRequestRepository.findByStatus(RequestStatus.PENDING);
    }

    @Transactional
    public ExtensionRequest approveRequest(ExtensionRequest request, User user, String comment) {
        request.setStatus(RequestStatus.APPROVED);
        request.setReviewedBy(user);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewerComment(comment);
        return extensionRequestRepository.save(request);
    }

    @Transactional
    public ExtensionRequest rejectRequest(ExtensionRequest request, User user, String comment) {
        request.setStatus(RequestStatus.REJECTED);
        request.setReviewedBy(user);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewerComment(comment);
        return extensionRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public Optional<ExtensionRequest> findLatestByDocument(Document document) {
        return extensionRequestRepository.findTopByDocumentOrderByCreatedAtDesc(document);
    }

    public List<ExtensionRequest> findByUser(User user) {
        return extensionRequestRepository.findByUser(user);
    }

    @Transactional(readOnly = true)
    public List<ExtensionRequest> findPendingRequestsByUsers(List<User> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        log.info("Fetching pending requests for {} users", users.size());
        return extensionRequestRepository.findByUserInAndStatus(users, RequestStatus.PENDING);
    }
}
