package org.info.tg_bot.model.enums;

import lombok.Getter;

@Getter
public enum RequestStatus {
    PENDING("Ожидает рассмотрения"),
    APPROVED("Одобрено"),
    REJECTED("Отклонено");

    private final String displayName;
    RequestStatus(String displayName) {
        this.displayName = displayName;
    }
}
