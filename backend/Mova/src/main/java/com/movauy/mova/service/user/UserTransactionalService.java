package com.movauy.mova.service.user;

import com.movauy.mova.model.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserTransactionalService {

    private static final Logger logger = LoggerFactory.getLogger(UserTransactionalService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void clearTokenVersionByUsername(String username) {
        try {
            User managed = entityManager.createQuery(
                    "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();

            logger.warn("🧹 Limpiando tokenVersion de '{}'. Valor actual: {}", username, managed.getTokenVersion());
            managed.setTokenVersion(null);
            logger.warn("✅ tokenVersion seteado a null en contexto JPA");

        } catch (NoResultException e) {
            logger.warn("⛔ No se encontró usuario con username '{}'. No se pudo limpiar tokenVersion.", username);
        } catch (Exception e) {
            logger.error("❌ Error inesperado al limpiar tokenVersion de '{}': {}", username, e.getMessage(), e);
            throw e; // Repropaga por si el controlador quiere manejarlo
        }
    }
}
