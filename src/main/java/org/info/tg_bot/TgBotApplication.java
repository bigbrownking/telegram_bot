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
import java.util.Set;
@SpringBootApplication
@EnableScheduling
public class TgBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TgBotApplication.class, args);
    }

    /*@Bean
    public CommandLineRunner run(EncryptionUtil encryptionUtil) {
        return args -> {
            String inputFile = "tg.csv";
            String outputFile = "tg_decrypted.csv";

            // Индексы колонок для расшифровки (0-based):
            Set<Integer> encryptedCols = Set.of(1, 2, 3);

            int total = 0, decrypted = 0, errors = 0;

            try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

                String line;
                boolean firstLine = true;

                while ((line = reader.readLine()) != null) {
                    if (firstLine) {
                        writer.write(line);
                        writer.newLine();
                        firstLine = false;
                        continue;
                    }

                    List<String> cols = parseCsvLine(line);
                    List<String> result = new ArrayList<>();

                    for (int i = 0; i < cols.size(); i++) {
                        String val = cols.get(i);
                        if (encryptedCols.contains(i) && val != null && !val.equals("NULL") && !val.isBlank()) {
                            try {
                                result.add(encryptionUtil.decrypt(val));
                                decrypted++;
                            } catch (Exception e) {
                                result.add(val); // оставляем как есть
                                errors++;
                            }
                        } else {
                            result.add(val);
                        }
                    }

                    writer.write(toCsvLine(result));
                    writer.newLine();
                    total++;

                    if (total % 5000 == 0) {
                        System.out.println("Обработано: " + total);
                    }
                }

                System.out.printf("%n✅ Готово! Строк: %d, расшифровано значений: %d, ошибок: %d%n", total, decrypted, errors);
                System.out.println("Результат: " + outputFile);

            } catch (Exception e) {
                System.err.println("❌ Ошибка: " + e.getMessage());
            }
        };
    }

    // Парсит строку CSV с учётом NULL и кавычек
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            if (line.startsWith("NULL", i)) {
                result.add("NULL");
                i += 4;
                if (i < line.length() && line.charAt(i) == ',') i++;
            } else if (line.charAt(i) == '"') {
                int end = i + 1;
                while (end < line.length()) {
                    if (line.charAt(end) == '"' && (end + 1 >= line.length() || line.charAt(end + 1) != '"')) {
                        break;
                    }
                    if (line.charAt(end) == '"') end++; // escaped quote
                    end++;
                }
                result.add(line.substring(i + 1, end).replace("\"\"", "\""));
                i = end + 1;
                if (i < line.length() && line.charAt(i) == ',') i++;
            } else {
                int end = line.indexOf(',', i);
                if (end == -1) end = line.length();
                result.add(line.substring(i, end));
                i = end + 1;
            }
        }
        return result;
    }

    // Собирает строку CSV обратно
    private String toCsvLine(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(',');
            String val = cols.get(i);
            if (val == null || val.equals("NULL")) {
                sb.append("NULL");
            } else {
                sb.append('"').append(val.replace("\"", "\"\"")).append('"');
            }
        }
        return sb.toString();
    }*/
}
