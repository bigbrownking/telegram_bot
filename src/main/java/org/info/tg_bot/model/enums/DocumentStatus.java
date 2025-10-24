package org.info.tg_bot.model.enums;

import lombok.Getter;

@Getter
public enum DocumentStatus {
    PENDING("На рассмотрении"),
    IN_PROGRESS("В работе"),
    COMPLETED("Исполнено"),
    OVERDUE("Просрочено"),
    REVISION("На доработке"),
    CANCELLED("Отменено");

    private final String displayName;

    DocumentStatus(String displayName) {
        this.displayName = displayName;
    }
}
