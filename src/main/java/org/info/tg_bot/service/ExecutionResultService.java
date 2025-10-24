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
}
