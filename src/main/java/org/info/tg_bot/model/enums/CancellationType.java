package org.info.tg_bot.model.enums;

import lombok.Getter;

@Getter
public enum CancellationType {
    ADMIN_DECISION("Решение администратора"),
    DUPLICATE("Дубликат документа"),
    ERROR("Создано ошибочно"),
    OTHER("Другая причина");

    private final String displayName;

    CancellationType(String displayName) {
        this.displayName = displayName;
    }
}
