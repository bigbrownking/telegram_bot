package org.info.tg_bot.model.enums;

import lombok.Getter;

@Getter
public enum ActionCategory {
    DOCUMENT("Документы"),
    USER("Пользователи"),
    NOTIFICATION("Уведомления"),
    EXTENSION("Продления"),
    RESULT("Результаты"),
    AUTH("Авторизация"),
    SYSTEM("Система");

    private final String displayName;

    ActionCategory(String displayName) {
        this.displayName = displayName;
    }
}