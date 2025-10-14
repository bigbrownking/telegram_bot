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

    public String getEmoji() {
        return switch (this) {
            case PENDING -> "⏳";
            case IN_PROGRESS -> "🔄";
            case COMPLETED -> "✅";
            case OVERDUE -> "🔴";
            case REVISION -> "🔙";
            case CANCELLED -> "❌";
        };
    }
}
