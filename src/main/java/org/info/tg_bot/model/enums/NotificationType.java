package org.info.tg_bot.model.enums;

import lombok.Getter;

@Getter
public enum NotificationType {
    FIVE_DAYS("5_days", -5, "За 5 дней до исполнения"),
    THREE_DAYS("3_days", -3, "За 3 дня до исполнения"),
    ONE_DAY("1_day", -1, "За 1 день до исполнения"),
    EXECUTION_DAY("execution_day", 0, "В день исполнения"),
    OVERDUE("overdue", 1, "Просрочка"),
    NEW_DOCUMENT("new_document", 0, "Новое поручение"),
    EXTENSION_APPROVED("extension_approved", 0, "Продление одобрено"),
    EXTENSION_REJECTED("extension_rejected", 0, "Продление отклонено"),
    RESULT_APPROVED("result_approved", 0, "Результат принят"),
    RESULT_REJECTED("result_rejected", 0, "Результат отклонен"),
    DOCUMENT_CANCELLED("document_cancelled", 0, "Поручение отменено");

    private final String code;
    private final int daysBefore;
    private final String displayName;

    NotificationType(String code, int daysBefore, String displayName) {
        this.code = code;
        this.daysBefore = daysBefore;
        this.displayName = displayName;
    }

    public boolean isDeadlineNotification() {
        return this == FIVE_DAYS || this == THREE_DAYS ||
                this == ONE_DAY || this == EXECUTION_DAY || this == OVERDUE;
    }
}

