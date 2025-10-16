package org.info.tg_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.model.entity.Document;
import org.info.tg_bot.model.enums.DocumentStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverdueChecker {

    private final DocumentService documentService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 5 * * ?")
    public void checkOverdueDocuments() {
        LocalDate today = LocalDate.now();
        List<Document> activeDocuments = documentService.findActiveDocumentsNotCompletedOrCancelled(today);

        for (Document doc : activeDocuments) {
            if (doc.getExecutionDate() != null && doc.getExecutionDate().isBefore(today)) {
                long daysOverdue = ChronoUnit.DAYS.between(doc.getExecutionDate(), today);
                doc.setStatus(DocumentStatus.OVERDUE);
                documentService.save(doc);

                if (doc.getExecutor() != null && doc.getExecutor().getTelegramId() != null) {
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
                    notificationService.notifyUser(doc.getExecutor().getTelegramId(), message, null);
                    log.info("Notified user {} about overdue task {} ({} days overdue)",
                            doc.getExecutor().getTelegramId(), doc.getRegNumber(), daysOverdue);
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
