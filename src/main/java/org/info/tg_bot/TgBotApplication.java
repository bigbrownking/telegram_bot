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
    CommandLineRunner run(EncryptionUtil encryptionUtil) {
        return args -> {
            List<String> encryptedStrings = List.of("xsX+nqm/me36LjJzx0aeMA==",	"8lOYjHYcFDL9qOxn0I35WbLzIWognlWgAdmcW14Dudo=",	"Kw8IqDuBm8hP1cQnVDvOYs2cE2gAmdO9IglmjekWRvk=");

            log.info("🔐 Attempting to decrypt {} strings...", encryptedStrings.size());

            StringBuilder result = new StringBuilder();
            int count = 0;

            for (String encrypted : encryptedStrings) {
                try {
                    String decrypted = encryptionUtil.decrypt(encrypted);
                    result.append(decrypted).append(" ");
                    count++;

                    if (count % 3 == 0) {
                        result.append("\n");
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to decrypt '{}': {}", encrypted, e.getMessage());
                }
            }

            log.info("✅ Decrypted:\n{}", result);
        };
    }
}
