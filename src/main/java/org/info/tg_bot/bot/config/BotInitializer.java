package org.info.tg_bot.bot.config;

import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.common.AdminBot;
import org.info.tg_bot.bot.common.UserBot;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

import java.util.List;

@Slf4j
@Component
public class BotInitializer {

    private final UserBot userBot;
    private final AdminBot adminBot;

    public BotInitializer(UserBot userBot, AdminBot adminBot) {
        this.userBot = userBot;
        this.adminBot = adminBot;
    }

    @Bean
    public ApplicationRunner initializeBotCommands() {
        return args -> {
            List<BotCommand> userCommands = List.of(
                    new BotCommand("start", "Зарегистрироваться или начать работу"),
                    new BotCommand("mytasks", "Посмотреть ваши задачи"),
                    new BotCommand("help", "Показать это сообщение")
            );
            userBot.execute(new SetMyCommands(userCommands, null, null));

            List<BotCommand> adminCommands = List.of(
                    new BotCommand("start", "Начать работу администратора"),
                    new BotCommand("upload", "Загрузить Excel файл"),
                    new BotCommand("help", "Показать справку")
            );
            adminBot.execute(new SetMyCommands(adminCommands, null, null));

            log.info("✅ UserBot and AdminBot commands initialized");
        };
    }
}
