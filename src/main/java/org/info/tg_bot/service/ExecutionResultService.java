package org.info.tg_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.ExecutionResult;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.repository.ExecutionResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionResultService {

    private final ExecutionResultRepository executionResultRepository;

    public ExecutionResult save(ExecutionResult executionResult) {
        return executionResultRepository.save(executionResult);
    }

    public Optional<ExecutionResult> findById(Long id) {
        return executionResultRepository.findById(id);
    }

    public List<ExecutionResult> findAll() {
        return executionResultRepository.findAll();
    }

    public void deleteById(Long id) {
        executionResultRepository.deleteById(id);
    }

    public Optional<ExecutionResult> findLatestByDocumentAndUser(Document document, User user) {
        return executionResultRepository.findFirstByDocumentAndUserOrderBySubmittedAtDesc(document, user);
    }

    public List<ExecutionResult> findAllByDocument(Document document) {
        return executionResultRepository.findAllByDocumentOrderBySubmittedAtDesc(document);
    }

    @Transactional
    public ExecutionResult submitResult(Document document, User user, String resultText, String filePath) {

        ExecutionResult newResult = ExecutionResult.builder()
                .document(document)
                .user(user)
                .resultText(resultText)
                .submittedAt(LocalDateTime.now())
                .build();

        log.info("✅ New execution result submitted by user {} for document {}", user.getId(), document.getId());
        return executionResultRepository.save(newResult);
    }

    @Transactional
    public void reviewResult(ExecutionResult result, User reviewer, String feedback) {
        result.setReviewedBy(reviewer);
        result.setReviewedAt(LocalDateTime.now());
        result.setAdminFeedback(feedback);
        executionResultRepository.save(result);

        log.info("🔍 Execution result {} reviewed by admin {}", result.getId(), reviewer.getId());
    }
}
