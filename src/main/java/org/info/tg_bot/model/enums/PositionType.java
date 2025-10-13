package org.info.tg_bot.model.enums;

import java.util.Arrays;

public enum PositionType {
    АДМИНИСТРАТИВНЫЙ_СЕКРЕТАРЬ,
    АРХИВАРИУС,
    В_РАСПОРЯЖЕНИИ,
    ГЛАВНЫЙ_КОНСУЛЬТАНТ_А,
    ГЛАВНЫЙ_КРИМИНАЛИСТ,
    ГЛАВНЫЙ_ЭКСПЕРТ_А,
    ДЕЛОПРОИЗВОДИТЕЛЬ,
    ЗАМЕСТИТЕЛЬ_ПРЕДСЕДАТЕЛЯ_ПО_ОПЕРАТИВНОЙ_РАБОТЕ,
    ЗАМЕСТИТЕЛЬ_РУКОВОДИТЕЛЯ_ДЕПАРТАМЕНТА,
    ЗАМЕСТИТЕЛЬ_РУКОВОДИТЕЛЯ_УПРАВЛЕНИЯ,
    КАДРОВЫЙ_РЕЗЕРВ,
    КРИМИНАЛИСТ,
    ОПЕРУПОЛНОМОЧЕННЫЙ_ПО_ОСОБО_ВАЖНЫМ_ДЕЛАМ,
    ОТКОМАНДИРОВАНИЕ,
    ПОМОЩНИК_ПРЕДСЕДАТЕЛЯ,
    ПОМОЩНИК_ПРЕДСЕДАТЕЛЯ_ПО_РЕЖИМУ,
    ПЕРВЫЙ_ЗАМЕСТИТЕЛЬ_ПРЕДСЕДАТЕЛЯ,
    ПРЕДСЕДАТЕЛЬ,
    РУКОВОДИТЕЛЬ_АППАРАТА,
    РУКОВОДИТЕЛЬ_ДЕПАРТАМЕНТА,
    РУКОВОДИТЕЛЬ_УПРАВЛЕНИЯ,
    РУКОВОДИТЕЛЬ_УПРАВЛЕНИЯ_А,
    СЕКРЕТАРЬ,
    СЛЕДОВАТЕЛЬ_ПО_ОСОБО_ВАЖНЫМ_ДЕЛАМ,
    СОВЕТНИК_ПРЕДСЕДАТЕЛЯ,
    СТАРШИЙ_КРИМИНАЛИСТ,
    СТАРШИЙ_ОПЕРУПОЛНОМОЧЕННЫЙ,
    СТАРШИЙ_СЛЕДОВАТЕЛЬ,
    СТАТИСТИК,
    ЭКСПЕРТ_А;

    public String getDisplayName() {
        return name().replace('_', ' ');
    }

    public static PositionType fromDisplayName(String text) {
        if (text == null || text.isBlank()) return null;
        String normalized = text
                .trim()
                .toUpperCase()
                .replace('Ё', 'Е')
                .replace(' ', '_');

        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(normalized)
                        || p.getDisplayName().equalsIgnoreCase(text)
                        || p.getDisplayName().equalsIgnoreCase(text.replace('ё', 'е')))
                .findFirst()
                .orElse(null);
    }
}
