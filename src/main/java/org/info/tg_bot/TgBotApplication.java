package org.info.tg_bot;

import org.info.tg_bot.util.EncryptionUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class TgBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TgBotApplication.class, args);
    }

   /* @Bean
    public CommandLineRunner run(EncryptionUtil encryptionUtil) {
        return args -> {
            String inputFile = "data.csv";
            String outputFile = "data_decrypted.csv";

            try (
                    BufferedReader reader = new BufferedReader(new FileReader(inputFile));
                    BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))
            ) {
                String line;
                boolean firstLine = true;

                while ((line = reader.readLine()) != null) {
                    // Пропускаем заголовок
                    if (firstLine) {
                        writer.write(line);
                        writer.newLine();
                        firstLine = false;
                        continue;
                    }

                    // Парсим: "ДЕПАРТАМЕНТ","зашифр_фамилия зашифр_имя зашифр_отчество"
                    String[] columns = line.split("\",\"");
                    String department = columns[0].replace("\"", "").trim();
                    String encryptedFio = columns[1].replace("\"", "").trim();

                    // Каждая часть ФИО зашифрована отдельно, разделены пробелом
                    String[] fioParts = encryptedFio.split(" ");
                    StringBuilder decryptedFio = new StringBuilder();

                    for (String part : fioParts) {
                        if (!decryptedFio.isEmpty()) decryptedFio.append(" ");
                        try {
                            decryptedFio.append(encryptionUtil.decrypt(part));
                        } catch (Exception e) {
                            decryptedFio.append("[ошибка]");
                            System.err.println("Ошибка расшифровки: " + part + " → " + e.getMessage());
                        }
                    }

                    String resultLine = "\"" + department + "\",\"" + decryptedFio + "\"";
                    writer.write(resultLine);
                    writer.newLine();

                    System.out.println(department + " | " + decryptedFio);
                }

                System.out.println("\n✅ Готово! Результат сохранён в: " + outputFile);

            } catch (Exception e) {
                System.err.println("❌ Ошибка чтения файла: " + e.getMessage());
            }
        };
    }*/
}
