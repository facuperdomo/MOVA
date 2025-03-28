package com.movauy.mova.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jasypt.util.text.BasicTextEncryptor;

@Converter
public class AttributeEncryptor implements AttributeConverter<String, String> {
    private final BasicTextEncryptor textEncryptor;

    public AttributeEncryptor() {
        textEncryptor = new BasicTextEncryptor();
        // Se obtiene la clave de encriptación desde la variable de entorno MY_ENCRYPTION_KEY
        String encryptionKey = System.getenv("MY_ENCRYPTION_KEY");
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            throw new IllegalStateException("La variable de entorno MY_ENCRYPTION_KEY es obligatoria y debe contener una clave fuerte (solo ASCII).");
        }
        textEncryptor.setPassword(encryptionKey);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return textEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return textEncryptor.decrypt(dbData);
    }
}
