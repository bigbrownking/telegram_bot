package org.info.tg_bot.model.enums;

import lombok.Getter;

@Getter
public enum RequestStatus {
    PENDING("Ожидает рассмотрения", "⏳"),
    APPROVED("Одобрено", "✅"),
    REJECTED("Отклонено", "❌");

    private final String displayName;
    private final String emoji;

    RequestStatus(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }
}
