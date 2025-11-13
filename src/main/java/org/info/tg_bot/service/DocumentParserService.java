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
import org.info.tg_bot.util.EncryptionUtil;
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
    private final EncryptionUtil encryptionUtil;
    private final ExecutionResultService executionResultService;

    private static final Map<String, Set<String>> FLEXIBLE_HEADER_MAP = Map.of(
            "regNumber", Set.of(
                    "рег номер", "номер обращения", "регномер", "номер",
                    "рег номер и дата входящего документа",
                    "рег номер и дата"
            ),
            "executionDate", Set.of(
                    "срок", "срок исполнения", "дата исполнения", "дата"
            ),
            "department", Set.of(
                    "сп", "подразделение", "структурное подразделение", "государственный орган", "отдел"
            ),
            "summary", Set.of(
                    "поручение", "краткое содержание", "содержание", "суть", "описание", "текст"
            ),
            "executor", Set.of(
                    "ответственный",
                    "ответственный сотрудник",
                    "ответственый сотрудник",
                    "ответственный исполнитель",
                    "ответственый исполнитель",
                    "исполнитель",
                    "сотрудник"
            )

    );

    public List<Document> parse(InputStream inputStream, User uploader) {
        List<TempDocument> tempDocuments = new ArrayList<>();
        Set<String> uniqueRegNumbers = new HashSet<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            int totalSheets = workbook.getNumberOfSheets();
            log.info("Excel file contains {} sheet(s)", totalSheets);

            List<Document> allProcessedDocs = new ArrayList<>();

            for (int sheetIndex = 0; sheetIndex < totalSheets; sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName().trim();

                if (sheet.getPhysicalNumberOfRows() <= 1) {
                    log.info("Skipping empty sheet: '{}'", sheetName);
                    continue;
                }

                log.info("Processing sheet: '{}' ({} rows)", sheetName, sheet.getPhysicalNumberOfRows());

                Iterator<Row> rows = sheet.iterator();
                if (!rows.hasNext()) continue;

                Row headerRow = rows.next();
                Map<Integer, String> columnMap = buildColumnMap(headerRow);

                if (!columnMap.containsValue("regNumber")) {
                    log.warn("Sheet '{}' skipped: no registration number column found. Headers: {}",
                            sheetName, getHeaderNames(headerRow, columnMap));
                    continue;
                }

                int sheetDocCount = 0;
                while (rows.hasNext()) {
                    Row row = rows.next();
                    if (isRowEmpty(row)) continue;

                    TempDocument.TempDocumentBuilder builder = TempDocument.builder();
                    User executorUser = null;
                    String regNumber = null;
                    LocalDate executionDate = null;

                    for (Cell cell : row) {
                        String field = columnMap.get(cell.getColumnIndex());
                        if (field == null) continue;

                        switch (field) {
                            case "regNumber" -> {
                                regNumber = getString(cell);
                                if (regNumber != null) regNumber = regNumber.trim();
                                builder.regNumber(regNumber);
                            }
                            case "summary" -> builder.summary(getString(cell));
                            case "department" -> builder.department(getString(cell));
                            case "executionDate" -> {
                                executionDate = getLocalDate(cell);
                                builder.executionDate(executionDate);
                            }
                            case "executor" -> {
                                String executorRaw = getString(cell);
                                if (executorRaw != null && !executorRaw.isBlank()) {
                                    executorRaw = executorRaw
                                            .replaceAll("\\s*\\([^)]*\\)", "")
                                            .replaceAll("\\s+", " ")
                                            .trim();

                                    try {
                                        User matched = findUserByShortName(executorRaw);
                                        if (matched != null) {
                                            executorUser = matched;
                                        } else {
                                            log.warn("No user match for executor: '{}' (sheet: {})", executorRaw, sheetName);
                                        }
                                    } catch (Exception e) {
                                        log.error("Error finding user: {}", executorRaw, e);
                                    }
                                }
                            }
                        }
                    }

                    if (regNumber == null || regNumber.isBlank()) {
                        log.warn("Skipping row without regNumber (sheet: {})", sheetName);
                        continue;
                    }

                    if (!uniqueRegNumbers.add(regNumber)) {
                        log.warn("Duplicate regNumber '{}' — skipping (sheet: {})", regNumber, sheetName);
                        continue;
                    }

                    DocumentStatus status = determineStatus(executionDate, null);

                    TempDocument tempDoc = builder
                            .status(status)
                            .uploadedBy(uploader)
                            .executor(executorUser)
                            .build();

                    log.debug("Creating tempDocument: {}", tempDoc);
                    tempDocuments.add(tempDoc);
                    sheetDocCount++;

                    if (executorUser != null) {
                        executorUser.getDocuments().add(convertToDocument(tempDoc, uploader));
                    }
                }

                log.info("Sheet '{}' processed: {} documents", sheetName, sheetDocCount);
            }

            if (tempDocuments.isEmpty()) {
                log.info("No valid documents found across all sheets");
                return new ArrayList<>();
            }

            tempDocumentRepository.saveAll(tempDocuments);
            log.info("Total {} temporary documents saved from {} sheets", tempDocuments.size(), totalSheets);

            log.debug("Saved regNumbers: {}",
                    tempDocuments.stream()
                            .map(TempDocument::getRegNumber)
                            .collect(Collectors.toList()));

            auditLogService.logAction(
                    uploader, "PARSE_EXCEL_MULTI", ActionCategory.SYSTEM, "TempDocument", null,
                    "Parsed Excel with multiple sheets", null, null, "Telegram Bot",
                    uploader.getTelegramUsername(), true, null,
                    Map.of("sheetCount", totalSheets, "documentCount", tempDocuments.size())
            );

            List<Document> processedDocs = processTempDocuments(tempDocuments, uploader);
            allProcessedDocs.addAll(processedDocs);

            // === АВТОЗАВЕРШЕНИЕ ОТСУТСТВУЮЩИХ ===
            List<String> tempRegNumbers = tempDocuments.stream()
                    .map(TempDocument::getRegNumber)
                    .filter(Objects::nonNull)
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
                    log.info("Updated existing ExecutionResult for document {}", doc.getRegNumber());
                } else {
                    resultToSave = ExecutionResult.builder()
                            .document(doc)
                            .user(doc.getExecutor() != null ? doc.getExecutor() : doc.getUploadedBy())
                            .resultText("Task completed automatically (absent in temp upload)")
                            .submittedAt(LocalDateTime.now())
                            .build();
                    log.info("Created new ExecutionResult for document {}", doc.getRegNumber());
                }

                executionResultService.save(resultToSave);

                auditLogService.logAction(
                        doc.getUploadedBy(), "MOVE_TO_EXECUTION_RESULT", ActionCategory.SYSTEM,
                        "Document", doc.getId(),
                        String.format("Document %s moved to execution_results (absent in temp upload)", doc.getRegNumber()),
                        null, null, "Telegram Bot",
                        doc.getUploadedBy() != null ? doc.getUploadedBy().getTelegramUsername() : null,
                        true, null, Map.of("regNumber", doc.getRegNumber())
                );
            }

            log.info("Excel parse completed. {} sheets, {} total docs processed, {} auto-completed",
                    totalSheets, allProcessedDocs.size(), documentsToComplete.size());

            return allProcessedDocs;

        } catch (Exception e) {
            log.error("Error parsing multi-sheet Excel", e);
            auditLogService.logAction(
                    uploader, "PARSE_EXCEL_ERROR", ActionCategory.SYSTEM, "TempDocument", null,
                    "Failed to parse multi-sheet Excel", null, null, "Telegram Bot",
                    uploader.getTelegramUsername(), false, e.getMessage(), null
            );
            return new ArrayList<>();
        }
    }
    private DocumentStatus determineStatus(LocalDate executionDate, DocumentStatus currentStatus) {
        if (executionDate == null) {
            return DocumentStatus.PENDING;
        }

        LocalDate today = LocalDate.now();

        if (executionDate.isBefore(today)) {
            return (currentStatus == DocumentStatus.COMPLETED) ? DocumentStatus.COMPLETED : DocumentStatus.OVERDUE;
        } else {
            return DocumentStatus.IN_PROGRESS;
        }
    }

    private Map<Integer, String> buildColumnMap(Row headerRow) {
        Map<Integer, String> columnMap = new HashMap<>();
        List<String> unknownHeaders = new ArrayList<>();

        for (Cell cell : headerRow) {
            String header = getString(cell);
            if (header == null || header.isBlank()) continue;

            String normalized = normalize(header);
            int colIndex = cell.getColumnIndex();

            if (columnMap.containsKey(colIndex)) continue;

            boolean matched = false;
            for (Map.Entry<String, Set<String>> entry : FLEXIBLE_HEADER_MAP.entrySet()) {
                if (entry.getValue().stream().anyMatch(normalized::equals)) {
                    columnMap.put(colIndex, entry.getKey());
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                unknownHeaders.add(header);
            }
        }

        if (!unknownHeaders.isEmpty()) {
            log.info("Ignored headers in sheet '{}': {}", headerRow.getSheet().getSheetName(), unknownHeaders);
        }

        log.info("Detected columns in '{}': {}", headerRow.getSheet().getSheetName(),
                columnMap.entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> getString(headerRow.getCell(e.getKey())),
                                Map.Entry::getValue
                        )));

        return columnMap;
    }
    private String normalize(String s) {
        return s.toLowerCase()
                .replaceAll("[^а-яa-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
    private List<String> getHeaderNames(Row headerRow, Map<Integer, String> columnMap) {
        return columnMap.keySet().stream()
                .map(idx -> getString(headerRow.getCell(idx)))
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getString(cell);
                if (val != null && !val.isBlank()) return false;
            }
        }
        return true;
    }

    private String getString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    double num = cell.getNumericCellValue();
                    if (num == (long) num) {
                        yield String.valueOf((long) num);
                    } else {
                        yield String.valueOf(num);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private LocalDate getLocalDate(Cell cell) {
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate();
        }

        String val = getString(cell);
        if (val == null || val.isBlank()) return null;

        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("d.M.yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
        );

        for (DateTimeFormatter fmt : formats) {
            try {
                return LocalDate.parse(val.trim(), fmt);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private List<Document> processTempDocuments(List<TempDocument> tempDocuments, User uploader) {
        List<Document> finalDocuments = new ArrayList<>();
        Map<String, TempDocument> tempMap = tempDocuments.stream()
                .collect(Collectors.toMap(TempDocument::getRegNumber, t -> t));

        List<Document> existingDocuments = documentRepository.findByRegNumberIn(tempMap.keySet());

        for (Document existingDoc : existingDocuments) {
            TempDocument tempDoc = tempMap.get(existingDoc.getRegNumber());
            if (tempDoc != null && updateDocument(existingDoc, tempDoc, uploader)) {
                finalDocuments.add(existingDoc);
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
                                            Вам назначен новый документ
                                            • %s
                                            Срок исполнения: %s
                                            """,
                                    newDoc.getRegNumber() != null ? newDoc.getRegNumber() : "(без номера)",
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
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("summary", existingDoc.getSummary());
        oldValues.put("department", existingDoc.getDepartment());
        oldValues.put("executionDate", existingDoc.getExecutionDate());
        oldValues.put("executorId", existingDoc.getExecutor() != null ? existingDoc.getExecutor().getId() : null);

        User oldExecutor = existingDoc.getExecutor();
        User newExecutor = tempDoc.getExecutor();

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

            DocumentStatus newStatus = determineStatus(tempDoc.getExecutionDate(), existingDoc.getStatus());
            if (newStatus != existingDoc.getStatus()) {
                existingDoc.setStatus(newStatus);
                updated = true;
            }
        }
        if (newExecutor != null && (oldExecutor == null || !oldExecutor.getId().equals(newExecutor.getId()))) {
            existingDoc.setExecutor(newExecutor);
            updated = true;
            if (newExecutor.getTelegramId() != null) {
                notificationService.notifyUser(
                        newExecutor.getTelegramId(),
                        String.format("""
                                    Вам назначен документ
                                    • %s
                                    Срок исполнения: %s
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

            auditLogService.logAction(
                    uploader, "UPDATE_DOCUMENT", org.info.tg_bot.model.enums.ActionCategory.DOCUMENT,
                    "Document", existingDoc.getId(), "Document updated from temp",
                    oldValues, newValues, "Telegram Bot", uploader.getTelegramUsername(),
                    true, null, Map.of("regNumber", existingDoc.getRegNumber())
            );

            if (existingDoc.getExecutor() != null && existingDoc.getExecutor().getTelegramId() != null) {
                notificationService.notifyUser(
                        existingDoc.getExecutor().getTelegramId(),
                        String.format("""
                                    Ваш документ обновлён
                                    • %s
                                    Новый срок: %s
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
        LocalDate execDate = tempDoc.getExecutionDate();
        DocumentStatus status = determineStatus(execDate, tempDoc.getStatus());

        return Document.builder()
                .regNumber(tempDoc.getRegNumber())
                .summary(tempDoc.getSummary())
                .department(tempDoc.getDepartment())
                .executionDate(execDate)
                .status(status)
                .uploadedBy(uploader)
                .executor(tempDoc.getExecutor())
                .build();
    }

    private User findUserByShortName(String shortName) throws Exception {
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

        String encryptedSurname = encryptionUtil.encrypt(surname);
        List<User> candidates = userRepository.findBySurnameIgnoreCase(encryptedSurname);

        for (User user : candidates) {
            boolean nameMatch = nameInitial == null || user.getName().toUpperCase().startsWith(nameInitial);
            boolean fatherMatch = fatherInitial == null || (user.getFathername() != null && user.getFathername().toUpperCase().startsWith(fatherInitial));
            if (nameMatch && fatherMatch) {
                return user;
            }
        }
        return null;
    }
}