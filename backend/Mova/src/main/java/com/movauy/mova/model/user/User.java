package com.movauy.mova.model.user;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@Entity
@Table(name = "user", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"username"})
})
public class User implements UserDetails {

    @Id
    @GeneratedValue
    private Long id;

    @Basic
    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "company_id")
    private String companyId;

    @Column(name = "mercadopago_access_token")
    @Convert(converter = com.movauy.mova.util.AttributeEncryptor.class)
    private String mercadoPagoAccessToken;

    // Constructor manual para usar en consultas que eviten el AttributeConverter
    public User(Long id, String username, String password, Role role, String companyId, String ignoredToken) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.companyId = companyId;
        this.mercadoPagoAccessToken = null; // No se carga
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", role=" + role +
                ", companyId='" + companyId + '\'' +
                ", mercadoPagoAccessToken='" + mercadoPagoAccessToken + '\'' +
                '}';
    }
}
