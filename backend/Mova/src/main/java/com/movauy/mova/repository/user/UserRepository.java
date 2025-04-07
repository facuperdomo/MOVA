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

    @Query("SELECT new com.movauy.mova.dto.UserBasicDTO(u.id, u.username, u.role, u.companyId) FROM User u WHERE u.id = :id")
    Optional<UserBasicDTO> findUserBasicById(@Param("id") Long id);

    @Query("SELECT new com.movauy.mova.model.user.User(u.id, u.username, u.password, u.role, u.companyId, null) "
            + "FROM User u WHERE u.id = :id")
    Optional<User> findUserWithoutSensitiveData(Long id);
}
