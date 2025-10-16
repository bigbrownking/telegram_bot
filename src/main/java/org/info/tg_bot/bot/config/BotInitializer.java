package org.info.tg_bot.bot.config;

import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.bot.common.AdminBot;
import org.info.tg_bot.bot.common.UserBot;
import org.info.tg_bot.service.FileForwardingService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.commands.DeleteMyCommands;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;

@Configuration
@Slf4j
public class BotInitializer {

    private final UserBot userBot;
    private final AdminBot adminBot;
    private final FileForwardingService fileForwardingService;

    public BotInitializer(UserBot userBot, AdminBot adminBot, FileForwardingService fileForwardingService) {
        this.userBot = userBot;
        this.adminBot = adminBot;
        this.fileForwardingService = fileForwardingService;
    }

    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(userBot);
        botsApi.registerBot(adminBot);

        userBot.execute(new DeleteMyCommands());
        adminBot.execute(new DeleteMyCommands());

        List<BotCommand> userCommands = List.of(
                new BotCommand("start", "Зарегистрироваться или начать работу"),
                new BotCommand("tasks", "Посмотреть ваши задачи"),
                new BotCommand("help", "Показать справку"),
                new BotCommand("pending", "Запросы на продление (для руководителей)")
        );
        userBot.execute(new SetMyCommands(userCommands, null, null));

        List<BotCommand> adminCommands = List.of(
                new BotCommand("start", "Начать работу администратора"),
                new BotCommand("upload", "Загрузить Excel файл"),
                new BotCommand("tasks", "Отправленные документы"),
                new BotCommand("help", "Показать справку"),
                new BotCommand("cancel", "Отменить документ")
        );
        adminBot.execute(new SetMyCommands(adminCommands, null, "ru"));

        log.info("✅ UserBot and AdminBot registered successfully");
        return botsApi;
    }

    @Bean
    public ApplicationRunner setFileForwarding() {
        return args -> {
                fileForwardingService.setUserBot(userBot);
                fileForwardingService.setAdminBot(adminBot);

                log.info("✅ File forwarding service set.");
        };
    }
}