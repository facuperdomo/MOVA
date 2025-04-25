package com.movauy.mova.repository.user;

import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.model.user.User;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 *
 * @author Facundo
 */
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    /**
     * Devuelve un DTO con exactamente los seis campos:
     * id, username, companyId, role, enableIngredients, enableKitchenCommands
     */
    @Query("""
        SELECT new com.movauy.mova.dto.UserBasicDTO(
            u.id,
            u.username,
            u.companyId,
            u.role.name(),
            u.enableIngredients,
            u.enableKitchenCommands
        )
        FROM User u
        WHERE u.id = :id
        """)
    Optional<UserBasicDTO> findUserBasicById(@Param("id") Long id);

    
}
