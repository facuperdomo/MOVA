// src/main/java/com/movauy/mova/repository/user/UserRepository.java
package com.movauy.mova.repository.user;

import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.model.user.Role;
import com.movauy.mova.model.user.User;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    @Query("""
        SELECT new com.movauy.mova.dto.UserBasicDTO(
            u.id,
            u.username,
            u.branch.id,
            u.branch.company.id,
            u.role.name(),
            u.branch.enableIngredients,
            u.branch.enableKitchenCommands
        )
        FROM User u
        WHERE u.id = :id
    """)
    Optional<UserBasicDTO> findUserBasicById(@Param("id") Long id);

    /**
     * Devuelve todos los usuarios de una sucursal.
     */
    List<User> findByBranch_Id(Long branchId);

    /**
     * Borra todos los usuarios de una sucursal (para delete-force).
     */
    void deleteByBranch_Id(Long branchId);

    /**
     * Verifica si ya existe un username en esa sucursal.
     */
    boolean existsByUsernameAndBranch_Id(String username, Long branchId);

    /**
     * Devuelve true si hay al menos un usuario asociado a la sucursal.
     */
    boolean existsByBranch_Id(Long branchId);
    
    List<User> findByAssignedBox_Id(Long boxId);
    
    // Todos los usuarios de una branch con rol ADMIN o USER
    List<User> findByBranch_IdAndRoleIn(Long branchId, Collection<Role> roles);

    // Todos los usuarios de una branch, rol ADMIN/USER, y que ya estén asignados a la caja boxId
    List<User> findByBranch_IdAndRoleInAndAssignedBox_Id(
        Long branchId,
        Collection<Role> roles,
        Long boxId
    );
}
