package org.info.tg_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.ExecutionResult;
import org.info.tg_bot.model.entity.TempDocument;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.ActionCategory;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.info.tg_bot.repository.DocumentRepository;
import org.info.tg_bot.repository.TempDocumentRepository;
import org.info.tg_bot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserService {

    private final NotificationService notificationService;
    private final TempDocumentRepository tempDocumentRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ExecutionResultService executionResultService;


    private static final Map<String, String> HEADER_MAP = Map.ofEntries(
            Map.entry("Рег. номер и дата входящего документа", "regNumber"),
            Map.entry("Регистрационный номер", "regNumber"),
            Map.entry("Номер обращения", "regNumber"),
            Map.entry("Краткое содержание", "summary"),
            Map.entry("Содержание", "summary"),
            Map.entry("Срок исполнения", "executionDate"),
            Map.entry("Дата исполнения", "executionDate"),
            Map.entry("Подразделение", "department"),
            Map.entry("Структурное подразделение", "department"),
            Map.entry("Государственный орган", "department"),
            Map.entry("Статус исполнения", "status"),
            Map.entry("Статус", "status"),
            Map.entry("Ответственный исполнитель", "executor"),
            Map.entry("Исполнитель", "executor")
    );

    public List<Document> parse(InputStream inputStream, User uploader) {
        List<TempDocument> tempDocuments = new ArrayList<>();
        Set<String> uniqueRegNumbers = new HashSet<>(); // Track unique regNumbers

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            if (!rows.hasNext()) return new ArrayList<>();
            Row headerRow = rows.next();

            Map<Integer, String> columnMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = cell.getStringCellValue().trim();
                HEADER_MAP.forEach((k, v) -> {
                    if (header.equalsIgnoreCase(k)) columnMap.put(cell.getColumnIndex(), v);
                });
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            while (rows.hasNext()) {
                Row row = rows.next();
                TempDocument.TempDocumentBuilder builder = TempDocument.builder();
                User executorUser = null;
                String regNumber = null;

                for (Cell cell : row) {
                    String field = columnMap.get(cell.getColumnIndex());
                    if (field == null) continue;

                    switch (field) {
                        case "regNumber" -> {
                            regNumber = getString(cell);
                            builder.regNumber(regNumber);
                        }
                        case "summary" -> builder.summary(getString(cell));
                        case "department" -> builder.department(getString(cell));
                        case "executionDate" -> builder.executionDate(getLocalDate(cell, formatter));
                        case "status" -> builder.status(DocumentStatus.IN_PROGRESS);
                        case "executor" -> {
                            String executorRaw = getString(cell);
                            if (executorRaw != null && !executorRaw.isBlank()) {
                                executorRaw = executorRaw.replaceAll("\\(.*?\\)", "").trim();
                                User matched = findUserByShortName(executorRaw);
                                if (matched != null) {
                                    executorUser = matched;
                                } else {
                                    log.warn("⚠️ No match found for executor: '{}'", executorRaw);
                                }
                            }
                        }
                    }
                }

                // Skip duplicate regNumbers
                if (regNumber != null && !uniqueRegNumbers.add(regNumber)) {
                    log.warn("⚠️ Skipping duplicate regNumber: '{}'", regNumber);
                    continue;
                }

                TempDocument tempDocument = builder
                        .status(DocumentStatus.IN_PROGRESS)
                        .uploadedBy(uploader)
                        .executor(executorUser)
                        .build();

                tempDocuments.add(tempDocument);

                if (executorUser != null) {
                    executorUser.getDocuments().add(convertToDocument(tempDocument, uploader));
                }
            }

            tempDocumentRepository.saveAll(tempDocuments);
            log.info("✅ {} temporary documents saved to temp table.", tempDocuments.size());

            auditLogService.logAction(
                    uploader,
                    "PARSE_EXCEL",
                    ActionCategory.SYSTEM,
                    "TempDocument",
                    null,
                    "Excel file parsed and saved to temp",
                    null,
                    null,
                    "Telegram Bot",
                    uploader.getTelegramUsername(),
                    true,
                    null,
                    Map.of("documentCount", tempDocuments.size())
            );

            // === PROCESS TEMP DOCUMENTS ===
            List<Document> processedDocs = processTempDocuments(tempDocuments, uploader);

            // === NEW LOGIC: MOVE MISSING DOCS TO EXECUTION_RESULTS ===
            List<String> tempRegNumbers = tempDocuments.stream()
                    .map(TempDocument::getRegNumber)
                    .toList();

            List<Document> allDocuments = documentRepository.findAll();
            List<Document> documentsToComplete = allDocuments.stream()
                    .filter(doc -> !tempRegNumbers.contains(doc.getRegNumber()))
                    .filter(doc -> !DocumentStatus.PENDING.equals(doc.getStatus()))
                    .toList();

            for (Document doc : documentsToComplete) {
                doc.setStatus(DocumentStatus.COMPLETED);
                documentRepository.save(doc);

                ExecutionResult latestResult = doc.getExecutionResults().stream()
                        .filter(Objects::nonNull)
                        .max(Comparator.comparing(ExecutionResult::getSubmittedAt))
                        .orElse(null);

                ExecutionResult resultToSave;
                if (latestResult != null) {
                    latestResult.setResultText("Task completed automatically (absent in temp upload)");
                    resultToSave = latestResult;
                    log.info("✅ Updated existing ExecutionResult for document {}", doc.getRegNumber());
                } else {
                    resultToSave = ExecutionResult.builder()
                            .document(doc)
                            .user(doc.getExecutor() != null ? doc.getExecutor() : doc.getUploadedBy())
                            .resultText("Task completed automatically (absent in temp upload)")
                            .submittedAt(LocalDateTime.now())
                            .build();
                    log.info("✅ Created new ExecutionResult for document {}", doc.getRegNumber());
                }

                executionResultService.save(resultToSave);

                auditLogService.logAction(
                        doc.getUploadedBy(),
                        "MOVE_TO_EXECUTION_RESULT",
                        ActionCategory.SYSTEM,
                        "Document",
                        doc.getId(),
                        String.format("Document %s moved to execution_results (absent in temp upload)", doc.getRegNumber()),
                        null,
                        null,
                        "Telegram Bot",
                        doc.getUploadedBy() != null ? doc.getUploadedBy().getTelegramUsername() : null,
                        true,
                        null,
                        Map.of("regNumber", doc.getRegNumber())
                );

                log.info("✅ Document {} moved to execution_results automatically.", doc.getRegNumber());
            }

            log.info("✅ Completed Excel parse. {} new/updated docs, {} auto-completed.",
                    processedDocs.size(), documentsToComplete.size());

            return processedDocs;

        } catch (Exception e) {
            log.error("❌ Error parsing Excel file", e);
            auditLogService.logAction(
                    uploader,
                    "PARSE_EXCEL",
                    ActionCategory.SYSTEM,
                    "TempDocument",
                    null,
                    "Failed to parse Excel file",
                    null,
                    null,
                    "Telegram Bot",
                    uploader.getTelegramUsername(),
                    false,
                    e.getMessage(),
                    null
            );
            return new ArrayList<>();
        }
    }

    private List<Document> processTempDocuments(List<TempDocument> tempDocuments, User uploader) {
        List<Document> finalDocuments = new ArrayList<>();
        Map<String, TempDocument> tempMap = tempDocuments.stream()
                .collect(Collectors.toMap(TempDocument::getRegNumber, t -> t));

        List<Document> existingDocuments = documentRepository.findByRegNumberIn(tempMap.keySet());

        for (Document existingDoc : existingDocuments) {
            TempDocument tempDoc = tempMap.get(existingDoc.getRegNumber());
            if (tempDoc != null) {
                boolean updated = updateDocument(existingDoc, tempDoc, uploader);
                if (updated) {
                    finalDocuments.add(existingDoc);
                }
            }
        }

        for (TempDocument tempDoc : tempDocuments) {
            if (existingDocuments.stream().noneMatch(d -> d.getRegNumber().equals(tempDoc.getRegNumber()))) {
                Document newDoc = convertToDocument(tempDoc, uploader);
                documentRepository.save(newDoc);
                finalDocuments.add(newDoc);

                if (newDoc.getExecutor() != null && newDoc.getExecutor().getTelegramId() != null) {
                    notificationService.notifyUser(
                            newDoc.getExecutor().getTelegramId(),
                            String.format("""
                                            📄 Вам назначен новый документ
                                            • %s
                                            📅 Срок исполнения: %s
                                            """,
                                    newDoc.getSummary() != null ? newDoc.getSummary() : "(без содержания)",
                                    newDoc.getExecutionDate() != null ? newDoc.getExecutionDate() : "не указан"),
                            null
                    );
                }
            }
        }
        return finalDocuments;
    }

    private boolean updateDocument(Document existingDoc, TempDocument tempDoc, User uploader) {
        boolean updated = false;
        Map<String, Map<String, Object>> changes = new HashMap<>();
        User oldExecutor = existingDoc.getExecutor();
        User newExecutor = tempDoc.getExecutor();

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("summary", existingDoc.getSummary());
        oldValues.put("department", existingDoc.getDepartment());
        oldValues.put("executionDate", existingDoc.getExecutionDate());
        oldValues.put("executorId", oldExecutor != null ? oldExecutor.getId() : null);

        if (!Objects.equals(existingDoc.getSummary(), tempDoc.getSummary())) {
            existingDoc.setSummary(tempDoc.getSummary());
            updated = true;
        }
        if (!Objects.equals(existingDoc.getDepartment(), tempDoc.getDepartment())) {
            existingDoc.setDepartment(tempDoc.getDepartment());
            updated = true;
        }
        if (!Objects.equals(existingDoc.getExecutionDate(), tempDoc.getExecutionDate())) {
            existingDoc.setExecutionDate(tempDoc.getExecutionDate());
            updated = true;
            LocalDate today = LocalDate.now();
            if (tempDoc.getExecutionDate() != null && tempDoc.getExecutionDate().isAfter(today)) {
                existingDoc.setStatus(DocumentStatus.IN_PROGRESS);
            }
        }
        if (newExecutor != null && (oldExecutor == null || !oldExecutor.getId().equals(newExecutor.getId()))) {
            existingDoc.setExecutor(newExecutor);
            updated = true;
            // Notify the new executor of the assignment
            if (newExecutor.getTelegramId() != null) {
                notificationService.notifyUser(
                        newExecutor.getTelegramId(),
                        String.format("""
                                    📄 Вам назначен новый документ
                                    • %s
                                    📅 Срок исполнения: %s
                                    """,
                                existingDoc.getSummary() != null ? existingDoc.getSummary() : "(без содержания)",
                                existingDoc.getExecutionDate() != null ? existingDoc.getExecutionDate() : "не указан"),
                        null
                );
            }
        }

        if (updated) {
            existingDoc.setUploadedBy(uploader);
            documentRepository.save(existingDoc);

            Map<String, Object> newValues = new HashMap<>();
            newValues.put("summary", existingDoc.getSummary());
            newValues.put("department", existingDoc.getDepartment());
            newValues.put("executionDate", existingDoc.getExecutionDate());
            newValues.put("executorId", existingDoc.getExecutor() != null ? existingDoc.getExecutor().getId() : null);

            // Compare and log changes
            changes.put("oldValues", oldValues);
            changes.put("newValues", newValues);

            auditLogService.logAction(
                    uploader,
                    "UPDATE_DOCUMENT",
                    org.info.tg_bot.model.enums.ActionCategory.DOCUMENT,
                    "Document",
                    existingDoc.getId(),
                    "Document updated from temp",
                    oldValues,
                    newValues,
                    "Telegram Bot",
                    uploader.getTelegramUsername(),
                    true,
                    null,
                    Map.of("regNumber", existingDoc.getRegNumber())
            );

            if (existingDoc.getExecutor() != null && existingDoc.getExecutor().getTelegramId() != null) {
                notificationService.notifyUser(
                        existingDoc.getExecutor().getTelegramId(),
                        String.format("""
                                    📄 Ваш документ обновлен
                                    • %s
                                    📅 Новый срок исполнения: %s
                                    """,
                                existingDoc.getSummary() != null ? existingDoc.getSummary() : "(без содержания)",
                                existingDoc.getExecutionDate() != null ? existingDoc.getExecutionDate() : "не указан"),
                        null
                );
            }
        }
        return updated;
    }

    private Document convertToDocument(TempDocument tempDoc, User uploader) {
        return Document.builder()
                .regNumber(tempDoc.getRegNumber())
                .summary(tempDoc.getSummary())
                .department(tempDoc.getDepartment())
                .executionDate(tempDoc.getExecutionDate())
                .status(tempDoc.getStatus())
                .uploadedBy(uploader)
                .executor(tempDoc.getExecutor())
                .build();
    }

    private String getString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private LocalDate getLocalDate(Cell cell, DateTimeFormatter formatter) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        } else {
            String val = getString(cell);
            try {
                return LocalDate.parse(val, formatter);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private User findUserByShortName(String shortName) {
        if (shortName == null || shortName.isBlank()) return null;

        String[] parts = shortName.split("\\s+");
        if (parts.length < 2) return null;

        String surname = parts[0];
        String initials = parts.length > 1 ? parts[1] : "";

        Matcher matcher = Pattern.compile("([А-ЯЁ])\\.\\s*([А-ЯЁ])?\\.?", Pattern.CASE_INSENSITIVE).matcher(initials);
        String nameInitial = null, fatherInitial = null;
        if (matcher.find()) {
            nameInitial = matcher.group(1).toUpperCase();
            if (matcher.group(2) != null) fatherInitial = matcher.group(2).toUpperCase();
        }

        List<User> candidates = userRepository.findBySurnameIgnoreCase(surname);
        for (User user : candidates) {
            boolean nameMatch = nameInitial == null || user.getName().toUpperCase().startsWith(nameInitial);
            boolean fatherMatch = fatherInitial == null || user.getFathername().toUpperCase().startsWith(fatherInitial);
            if (nameMatch && fatherMatch) {
                return user;
            }
        }
        return null;
    }
}