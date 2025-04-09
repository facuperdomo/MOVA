package com.movauy.mova.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jasypt.util.text.BasicTextEncryptor;

@Converter
public class AttributeEncryptor implements AttributeConverter<String, String> {
    
    private final BasicTextEncryptor textEncryptor;
    // Prefijo y sufijo para identificar los valores encriptados
    private static final String PREFIX = "ENC(";
    private static final String SUFFIX = ")";
    
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
        // Encripta el atributo y le añade el prefijo y sufijo para marcarlo
        String encrypted = textEncryptor.encrypt(attribute);
        return PREFIX + encrypted + SUFFIX;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // Solo se intenta desencriptar si el valor está marcado con el prefijo y sufijo
        if (dbData.startsWith(PREFIX) && dbData.endsWith(SUFFIX)) {
            String encrypted = dbData.substring(PREFIX.length(), dbData.length() - SUFFIX.length());
            return textEncryptor.decrypt(encrypted);
        } else {
            // Si no tiene el formato marcado, se devuelve tal cual (por ejemplo, para ventas en efectivo)
            return dbData;
        }
    }
}