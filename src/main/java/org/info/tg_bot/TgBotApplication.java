package org.info.tg_bot;

import lombok.extern.slf4j.Slf4j;
import org.info.tg_bot.util.EncryptionUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class TgBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TgBotApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(EncryptionUtil encryptionUtil) {
        return args -> {
        };
    }

}
