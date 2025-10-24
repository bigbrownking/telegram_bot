package org.info.tg_bot.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Converter
public class EncryptionConverter implements AttributeConverter<String, String> {

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        try {
            return attribute != null ? encryptionUtil.encrypt(attribute) : null;
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        try {
            return dbData != null ? encryptionUtil.decrypt(dbData) : null;
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}