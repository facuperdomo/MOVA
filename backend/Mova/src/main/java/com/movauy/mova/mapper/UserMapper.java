package com.movauy.mova.mapper;

import com.movauy.mova.model.user.Role;
import com.movauy.mova.model.user.User;
import com.movauy.mova.dto.UserBasicDTO;

public class UserMapper {

    // Convierte de entidad a DTO, incluyendo los flags de configuración
    public static UserBasicDTO toUserBasicDTO(User user) {
        if (user == null) {
            return null;
        }
        return new UserBasicDTO(
                user.getId(),
                user.getUsername(),
                user.getCompanyId(),
                user.getRole() != null ? user.getRole().name() : null,
                // Nuevos flags:
                user.isEnableIngredients(),
                user.isEnableKitchenCommands()
        );
    }

    // Convierte de DTO a entidad User (creando o actualizando uno existente)
    public static User toUser(UserBasicDTO dto, User existingUser) {
        if (dto == null) return null;

        existingUser.setUsername(dto.getUsername());
        existingUser.setCompanyId(dto.getCompanyId());

        if (dto.getRole() != null) {
            try {
                existingUser.setRole(Role.valueOf(dto.getRole()));
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Rol inválido: " + dto.getRole());
            }
        } else {
            throw new RuntimeException("El rol del usuario es null y es requerido.");
        }

        // NOTA: normalmente no exponemos estos flags para escritura desde el DTO,
        // pues son settings de la empresa que idealmente se controlan en otro flujo.
        // Si quieres permitir que sean modificables:
        // existingUser.setEnableIngredients(dto.isEnableIngredients());
        // existingUser.setEnableKitchenCommands(dto.isEnableKitchenCommands());

        return existingUser;
    }
}
