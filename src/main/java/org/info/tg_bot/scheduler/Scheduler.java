package org.info.tg_bot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.entity.User;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.info.tg_bot.model.enums.ActionCategory;
import org.info.tg_bot.service.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class Scheduler {

    private final DocumentService documentService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService; // ✅ Added

    @Scheduled(cron = "${scheduler.overdue-check.cron}")
    public void checkOverdueDocuments() {
        LocalDate today = LocalDate.now();
        List<Document> activeDocuments = documentService.findActiveDocumentsNotCompletedOrCancelled(today);

        for (Document doc : activeDocuments) {
            if (doc.getExecutionDate() != null && doc.getExecutionDate().isBefore(today)) {
                long daysOverdue = ChronoUnit.DAYS.between(doc.getExecutionDate(), today);
                doc.setStatus(DocumentStatus.OVERDUE);
                documentService.save(doc);

                User responsible = doc.getExecutor();
                if (responsible != null && responsible.getTelegramId() != null) {
                    String message = String.format(
                            "⚠ <b>Просрочено поручение</b>\n\n" +
                                    "📄 Поручение №%s\n" +
                                    "📝 Описание: %s\n" +
                                    "🗓 Срок исполнения: %s\n" +
                                    "⏰ Просрочено на %d %s\n",
                            doc.getRegNumber(),
                            doc.getSummary(),
                            doc.getExecutionDate(),
                            daysOverdue,
                            getDaysWord(daysOverdue)
                    );

                    notificationService.notifyUser(responsible.getTelegramId(), message, null);
                    User boss = userService.findBoss(responsible.getTelegramId());
                    notificationService.notifyUser(boss.getTelegramId(), message, null);

                    auditLogService.logAction(
                            responsible,
                            "OVERDUE_NOTIFICATION_SENT",
                            ActionCategory.NOTIFICATION,
                            "Document",
                            doc.getId(),
                            String.format("Просрочено поручение №%s отправлено исполнителю и руководителю", doc.getRegNumber()),
                            null,
                            null,
                            "Telegram Bot",
                            responsible.getTelegramUsername(),
                            true,
                            null,
                            Map.of("daysOverdue", daysOverdue)
                    );

                    log.info("Notified user {} and boss {} about overdue task {} ({} days overdue)",
                            responsible.getTelegramId(), boss.getTelegramId(), doc.getRegNumber(), daysOverdue);
                }
            }
        }
    }

    @Scheduled(cron = "${scheduler.upcoming-check.cron}")
    public void checkUpcomingDeadlines() {
        LocalDate today = LocalDate.now();
        List<Document> activeDocuments = documentService.findActiveDocumentsNotCompletedOrCancelled(today);

        for (Document doc : activeDocuments) {
            if (doc.getExecutionDate() != null) {
                long daysUntilDue = ChronoUnit.DAYS.between(today, doc.getExecutionDate());
                if (daysUntilDue == 1 || daysUntilDue == 3 || daysUntilDue == 5) {
                    User responsible = doc.getExecutor();
                    if (responsible != null && responsible.getTelegramId() != null) {
                        String message = String.format(
                                "⏰ <b>Напоминание о поручении</b>\n\n" +
                                        "📄 Поручение №%s\n" +
                                        "📝 Описание: %s\n" +
                                        "🗓 Срок исполнения: %s\n" +
                                        "⏳ Осталось %d %s\n",
                                doc.getRegNumber(),
                                doc.getSummary(),
                                doc.getExecutionDate(),
                                daysUntilDue,
                                getDaysWord(daysUntilDue)
                        );

                        notificationService.notifyUser(responsible.getTelegramId(), message, null);
                        User boss = userService.findBoss(responsible.getTelegramId());
                        notificationService.notifyUser(boss.getTelegramId(), message, null);

                        auditLogService.logAction(
                                responsible,
                                "UPCOMING_DEADLINE_NOTIFICATION_SENT",
                                ActionCategory.NOTIFICATION,
                                "Document",
                                doc.getId(),
                                String.format("Напоминание о поручении №%s (%d дней до срока)", doc.getRegNumber(), daysUntilDue),
                                null,
                                null,
                                "Telegram Bot",
                                responsible.getTelegramUsername(),
                                true,
                                null,
                                Map.of("daysUntilDue", daysUntilDue)
                        );

                        log.info("Notified user {} and boss {} about upcoming deadline for task {} ({} days remaining)",
                                responsible.getTelegramId(), boss.getTelegramId(), doc.getRegNumber(), daysUntilDue);
                    }
                }
            }
        }
    }

    private String getDaysWord(long days) {
        if (days % 10 == 1 && days % 100 != 11) {
            return "день";
        } else if (days % 10 >= 2 && days % 10 <= 4 && (days % 100 < 10 || days % 100 >= 20)) {
            return "дня";
        } else {
            return "дней";
        }
    }
}
