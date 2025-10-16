package org.info.tg_bot.model.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum DepartmentType {
    АДМИНИСТРАТИВНЫЙ_ДЕПАРТАМЕНТ,
    В_РАСПОРЯЖЕНИИ,
    ВНЕШТАТНЫЕ_СОТРУДНИКИ,
    ДЕПАРТАМЕНТ_АНТИКОРРУПЦИОННОГО_ФИНАНСОВОГО_МОНИТОРИНГА,
    ДЕПАРТАМЕНТ_КАДРОВОЙ_РАБОТЫ,
    ДЕПАРТАМЕНТ_ОПЕРАТИВНОГО_АНАЛИЗА,
    ДЕПАРТАМЕНТ_ПРАВОВОГО_ОБЕСПЕЧЕНИЯ,
    ДЕПАРТАМЕНТ_ПРЕВЕНЦИИ_И_АНАЛИТИЧЕСКИХ_РАЗРАБОТОК,
    ДЕПАРТАМЕНТ_СОБСТВЕННОЙ_БЕЗОПАСНОСТИ,
    ДЕПАРТАМЕНТ_СТРАТЕГИИ_И_ОПЕРАТИВНОГО_УПРАВЛЕНИЯ,
    ДЕПАРТАМЕНТ_ЦИФРОВИЗАЦИИ,
    ДЕПАРТАМЕНТ_ФИНАНСОВОГО_МОНИТОРИНГА_В_СФЕРЕ_ОТМЫВАНИЯ_ДОХОДОВ,
    ДЕПАРТАМЕНТ_ФИНАНСОВОГО_МОНИТОРИНГА_ПО_ФИНАНСИРОВАНИЮ_ТЕРРОРИЗМА,
    ДЕПАРТАМЕНТ_ПО_РАБОТЕ_С_СУБЪЕКТАМИ_ФИНМОНИТОРИНГА,
    КАДРОВЫЙ_РЕЗЕРВ,
    КРИМИНАЛИСТИЧЕСКОЕ_УПРАВЛЕНИЕ,
    ОПЕРАТИВНЫЙ_ДЕПАРТАМЕНТ,
    ОТКОМАНДИРОВАНИЕ,
    РУКОВОДСТВО,
    СЕКРЕТАРИАТ_ПРЕДСЕДАТЕЛЯ,
    СЛЕДСТВЕННЫЙ_ДЕПАРТАМЕНТ,
    СЛУЖБА_ВНУТРЕННЕГО_АУДИТА,
    УПРАВЛЕНИЕ_ПО_СМИ;

    public String getDisplayName() {
        return Arrays.stream(name().split("_"))
                .map(word -> {
                    if (word.isEmpty()) return word;
                    return Character.toUpperCase(word.charAt(0)) +
                            word.substring(1).toLowerCase();
                })
                .collect(Collectors.joining(" "));
    }

    public static DepartmentType fromDisplayName(String text) {
        if (text == null || text.isBlank()) return null;
        String normalized = text
                .trim()
                .toUpperCase()
                .replace('Ё', 'Е')
                .replace(' ', '_');

        return Arrays.stream(values())
                .filter(d -> d.name().equalsIgnoreCase(normalized)
                        || d.getDisplayName().equalsIgnoreCase(text)
                        || d.getDisplayName().equalsIgnoreCase(text.replace('ё', 'е')))
                .findFirst()
                .orElse(null);
    }
}
