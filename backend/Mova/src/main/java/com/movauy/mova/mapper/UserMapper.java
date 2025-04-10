package com.movauy.mova.mapper;

import com.movauy.mova.model.user.User;
import com.movauy.mova.dto.UserBasicDTO;

// Esta clase es responsable de mapear la entidad a DTO y viceversa
public class UserMapper {

    // Método para convertir de entidad User a UserBasicDTO
    public static UserBasicDTO toUserBasicDTO(User user) {
        if (user == null) {
            return null;
        }
        // Se mapea el id, username, companyId y role (como String)
        return new UserBasicDTO(
                user.getId(),
                user.getUsername(),
                user.getCompanyId(),
                user.getRole().name()
        );
    }

    // Si requieres convertir el DTO a la entidad, define otro método
    // Esto es útil, por ejemplo, en actualizaciones que no modifiquen el token
    public static User toUser(UserBasicDTO dto, User existingUser) {
        if (dto == null) {
            return null;
        }
        // Con esta estrategia, se actualizan solo los campos básicos,
        // dejando intacto el token (o se puede actualizar según la lógica)
        existingUser.setUsername(dto.getUsername());
        existingUser.setCompanyId(dto.getCompanyId());
        // Para el role, se asume que tienes un método para transformar la cadena en enum
        existingUser.setRole(Enum.valueOf(existingUser.getRole().getDeclaringClass(), dto.getRole()));
        return existingUser;
    }
}
