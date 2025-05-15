package com.movauy.mova.mapper;

import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.user.Role;
import com.movauy.mova.model.user.User;
import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.dto.UserCreateDTO;
import com.movauy.mova.dto.UserUpdateDTO;
import com.movauy.mova.repository.branch.BranchRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

public class UserMapper {

    /**
     * Convierte de entidad User a DTO, extrayendo el ID de la sucursal y de la
     * empresa.
     */
    public static UserBasicDTO toUserBasicDTO(User user) {
        if (user == null) {
            return null;
        }

        Long branchId = null, companyId = null;
        if (user.getBranch() != null) {
            branchId = user.getBranch().getId();
            if (user.getBranch().getCompany() != null) {
                companyId = user.getBranch().getCompany().getId();
            }
        }

        return new UserBasicDTO(
                user.getId(),
                user.getUsername(),
                branchId,
                companyId,
                user.getRole() != null ? user.getRole().name() : null,
                user.getBranch() != null && user.getBranch().isEnableIngredients(),
                user.getBranch() != null && user.getBranch().isEnableKitchenCommands()
        );
    }

    /**
     * Convierte de DTO a entidad User, asignando la relación Branch si viene el
     * ID, y estableciendo/actualizando la contraseña si dto.getPassword() no es
     * null.
     *
     * @param dto datos provenientes del cliente (debe incluir campo "password")
     * @param existingUser entidad a crear o actualizar
     * @param branchRepo repo para buscar la sucursal
     * @param encoder para codificar la contraseña
     */
    public static User toUser(UserCreateDTO dto,
            User existingUser,
            BranchRepository branchRepo,
            PasswordEncoder encoder) {
        existingUser.setUsername(dto.getUsername());
        existingUser.setPassword(encoder.encode(dto.getPassword()));
        existingUser.setRole(Role.valueOf(dto.getRole()));

        Branch branch = branchRepo.findById(dto.getBranchId())
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada: " + dto.getBranchId()));
        existingUser.setBranch(branch);

        return existingUser;
    }

    //
    // Para actualización: password opcional
    //
    public static User toUser(
            UserUpdateDTO dto,
            User existingUser,
            BranchRepository branchRepo,
            PasswordEncoder encoder
    ) {
        existingUser.setUsername(dto.getUsername());

        // sólo actualizamos la contraseña si viene no-nula y no-blank
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            existingUser.setPassword(encoder.encode(dto.getPassword()));
        }

        existingUser.setRole(Role.valueOf(dto.getRole()));

        Branch branch = branchRepo.findById(dto.getBranchId())
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada: " + dto.getBranchId()));
        existingUser.setBranch(branch);

        return existingUser;
    }
}
