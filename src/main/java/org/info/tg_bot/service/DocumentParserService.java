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
                    "рег номер и дата", "рег п", "протокол"
            ),
            "executionDate", Set.of(
                    "срок", "срок исполнения", "дата исполнения", "дата", "срок док та"
            ),
            "department", Set.of(
                    "сп", "подразделение", "структурное подразделение", "государственный орган", "отдел"
            ),
            "summary", Set.of(
                    "поручение", "краткое содержание", "содержание", "суть", "описание", "текст",
                    "кр сод основного д та кр сод резолюции", "фабула"
            ),
            "executor", Set.of(
                    "ответственный",
                    "ответственный сотрудник",
                    "ответственый сотрудник",
                    "ответственный исполнитель",
                    "ответственый исполнитель",
                    "исполнитель",
                    "сотрудник",
                    "ф и о сотрудника",
                    "фио сотрудника",
                    "фио"
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
                                    try {
                                        User matched = findUserByShortName(executorRaw);
                                        if (matched != null) {
                                            executorUser = matched;
                                            log.info("✓ Executor matched: {}", matched.getFullName());
                                        } else {
                                            log.warn("No user match for executor: '{}' (sheet: {})", executorRaw, sheetName);
                                        }
                                    } catch (Exception e) {
                                        log.error("Error finding user: '{}' - {}", executorRaw, e.getMessage(), e);
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

            List<String> completedRegNumbers = new ArrayList<>();

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
                        doc.getUploadedBy(), "AUTO_COMPLETE_DOCUMENT", ActionCategory.SYSTEM,
                        "Document", doc.getId(),
                        String.format("Document %s auto-completed (absent in temp upload)", doc.getRegNumber()),
                        null, null, "Telegram Bot",
                        doc.getUploadedBy() != null ? doc.getUploadedBy().getTelegramUsername() : null,
                        true, null, Map.of("regNumber", doc.getRegNumber())
                );
                completedRegNumbers.add(doc.getRegNumber());

            }

            //УДАЛЯЕМ ИЗ TEMP ТОЛЬКО ЗАВЕРШЁННЫЕ ДОКУМЕНТЫ
            if (!completedRegNumbers.isEmpty()) {
                List<TempDocument> tempToDelete = tempDocumentRepository.findAll().stream()
                        .filter(temp -> completedRegNumbers.contains(temp.getRegNumber()))
                        .collect(Collectors.toList());

                if (!tempToDelete.isEmpty()) {
                    tempDocumentRepository.deleteAll(tempToDelete);
                    log.info("✅ Deleted {} completed documents from temp table: {}",
                            tempToDelete.size(), completedRegNumbers);

                    auditLogService.logAction(
                            uploader,
                            "DELETE_COMPLETED_FROM_TEMP",
                            ActionCategory.SYSTEM,
                            "TempDocument",
                            null,
                            "Deleted completed documents from temp",
                            null,
                            null,
                            "Telegram Bot",
                            uploader.getTelegramUsername(),
                            true,
                            null,
                            Map.of("deletedCount", tempToDelete.size(), "regNumbers", completedRegNumbers)
                    );
                }
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

        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate();
            }

            // числовое значение — Excel serial number
            double numVal = cell.getNumericCellValue();
            if (numVal > 1000) { // защита от случайных малых чисел
                try {
                    return DateUtil.getLocalDateTime(numVal)
                            .toLocalDate();
                } catch (Exception e) {
                    log.warn("Failed to convert Excel serial number to date: {}", numVal);
                }
            }
        }

        String val = getString(cell);
        log.debug("Parsing date from string: '{}' (cell type: {})", val, cell.getCellType());

        if (val == null || val.isBlank()) return null;

        // Ячейка может содержать несколько дат (перенос строки или пробел),
        // например "14.07.2026 04.08.2026" — берём последнюю как фактический срок
        val = val.trim();
        if (val.matches(".*\\s.*")) {
            String[] tokens = val.split("\\s+");
            String lastToken = tokens[tokens.length - 1];
            log.debug("Multiple tokens in date cell '{}', using last: '{}'", val, lastToken);
            val = lastToken;
        }

        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("d.M.yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d.MM.yyyy"),
                DateTimeFormatter.ofPattern("dd.M.yyyy")
        );

        for (DateTimeFormatter fmt : formats) {
            try {
                return LocalDate.parse(val.trim(), fmt);
            } catch (Exception ignored) {}
        }

        log.warn("Failed to parse date: '{}'", val);
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
                                            •Номер документа:  %s
                                            •Краткое содержание: %s
                                            Срок исполнения: %s
                                            """,
                                    newDoc.getRegNumber() != null ? newDoc.getRegNumber() : "(без номера)",
                                    newDoc.getSummary() != null ? newDoc.getSummary() : "(без содержания)",
                                    newDoc.getExecutionDate() != null ? newDoc.getExecutionDate() : "не указан"),
                            null
                    );
                    userRepository.findDepartmentBoss(newDoc.getExecutor().getDepartment().getId()).ifPresent(depBoss -> notificationService.notifyUser(
                            depBoss.getTelegramId(),
                            String.format("""
                                            Вам как руководителю департамента назначен новый документ
                                            •Номер документа:  %s
                                            •Краткое содержание: %s
                                            •Исполнитель: %s
                                            Срок исполнения: %s
                                            """,
                                    newDoc.getRegNumber() != null ? newDoc.getRegNumber() : "(без номера)",
                                    newDoc.getSummary() != null ? newDoc.getSummary() : "(без содержания)",
                                    newDoc.getExecutor() != null ? newDoc.getExecutor().getFullName() : "(безымянный)",
                                    newDoc.getExecutionDate() != null ? newDoc.getExecutionDate() : "не указан"),
                            null
                    ));


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

    public User findUserByShortName(String shortName) throws Exception {
        if (shortName == null || shortName.isBlank()) return null;

        String cleaned = shortName
                .replaceAll("\\s*\\([^()]*\\)", "")
                .replaceAll("\\s*\\([^()]*\\)", "")
                .replaceAll("[()]", "")
                .replaceAll("\\s+", " ")
                .trim();

        String[] parts = cleaned.split("\\s+");
        if (parts.length < 2) {
            log.warn("Not enough parts in: '{}'", shortName);
            return null;
        }

        String surname = parts[0];

        // Извлекаем инициалы из оставшихся частей
        List<String> initials = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];

            // Убираем все точки и оставляем только буквы
            String lettersOnly = part.replaceAll("[^А-Яа-яЁёӘәІіҢңҒғҮүҰұӨөҚқҺһƏə]", "");

            if (lettersOnly.isEmpty()) continue;

            // 🔥 КЛЮЧЕВОЕ ИЗМЕНЕНИЕ:
            // Если короткая строка (1-2 буквы) - это инициалы, берем все буквы
            if (lettersOnly.length() <= 2) {
                for (char c : lettersOnly.toCharArray()) {
                    initials.add(String.valueOf(c));
                }
            } else {
                // Если длинная строка (>2 букв) - это полное имя, берем только первую букву
                initials.add(String.valueOf(lettersOnly.charAt(0)));
            }
        }

        if (initials.isEmpty()) {
            log.warn("No initials found in: '{}'", shortName);
            return null;
        }

        String nameInitial = initials.get(0);
        String fatherInitial = initials.size() > 1 ? initials.get(1) : null;

        log.debug("Parsing: surname='{}', nameInitial='{}', fatherInitial='{}'",
                surname, nameInitial, fatherInitial);

        String encryptedSurname = encryptionUtil.encrypt(surname);
        List<User> candidates = userRepository.findBySurnameIgnoreCase(encryptedSurname);

        log.debug("Found {} candidates for surname '{}'", candidates.size(), surname);

        for (User user : candidates) {
            String userName = user.getName() != null ? user.getName().toUpperCase() : "";
            String userFather = user.getFathername() != null ? user.getFathername().toUpperCase() : "";

            boolean nameMatch = !userName.isEmpty() && userName.startsWith(nameInitial);
            boolean fatherMatch = fatherInitial == null ||
                    userFather.isEmpty() ||
                    userFather.startsWith(fatherInitial);

            if (nameMatch && fatherMatch) {
                log.info("✓ Matched user: {} (from '{}')", user.getFullName(), shortName);
                return user;
            }
        }

        log.warn("No user found for: '{}'", shortName);
        return null;
    }
}