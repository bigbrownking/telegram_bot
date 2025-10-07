package org.info.tg_bot.model.enums;

import lombok.Getter;

@Getter
public enum UserRole {
    ADMIN("Администратор"),
    USER("Пользователь");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }
}