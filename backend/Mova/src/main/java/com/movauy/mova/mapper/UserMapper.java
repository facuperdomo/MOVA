package com.movauy.mova.mapper;

import com.movauy.mova.model.user.Role;
import com.movauy.mova.model.user.User;
import com.movauy.mova.dto.UserBasicDTO;

public class UserMapper {

    // Convierte de entidad a DTO
    public static UserBasicDTO toUserBasicDTO(User user) {
        if (user == null) {
            return null;
        }
        return new UserBasicDTO(
                user.getId(),
                user.getUsername(),
                user.getCompanyId(),
                user.getRole() != null ? user.getRole().name() : null
        );
    }

    // Convierte de DTO a entidad User (creando o actualizando uno existente)
    public static User toUser(UserBasicDTO dto, User existingUser) {
        if (dto == null) return null;

        existingUser.setId(dto.getId()); // <- opcional si querés conservar la ID
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

        return existingUser;
    }
}
