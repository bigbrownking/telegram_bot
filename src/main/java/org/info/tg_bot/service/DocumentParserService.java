package org.info.tg_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.info.tg_bot.repository.DocumentRepository;
import org.info.tg_bot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserService {

    private final NotificationService notificationService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

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
        List<Document> documents = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            if (!rows.hasNext()) return documents;
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
                Document.DocumentBuilder builder = Document.builder();
                User executorUser = null;

                for (Cell cell : row) {
                    String field = columnMap.get(cell.getColumnIndex());
                    if (field == null) continue;

                    switch (field) {
                        case "regNumber" -> builder.regNumber(getString(cell));
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

                Document document = builder
                        .status(DocumentStatus.IN_PROGRESS)
                        .uploadedBy(uploader)
                        .executor(executorUser)
                        .build();

                documents.add(document);

                if (executorUser != null) {
                    executorUser.getDocuments().add(document);
                }
            }

            documentRepository.saveAll(documents);
            log.info("✅ {} documents successfully saved.", documents.size());


            for (Document doc : documents) {
                if (doc.getExecutor() != null && doc.getExecutor().getTelegramId() != null) {
                    notificationService.notifyUser(
                            doc.getExecutor().getTelegramId(),
                            String.format("""
                                    📄 Вам назначен новый документ
                                    • %s
                                    📅 Срок исполнения: %s
                                    """,
                                    doc.getSummary() != null ? doc.getSummary() : "(без содержания)",
                                    doc.getExecutionDate() != null ? doc.getExecutionDate() : "не указан"),
                            null
                    );
                }
            }

        } catch (Exception e) {
            log.error("❌ Error parsing Excel file", e);
        }

        return documents;
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
    }}
