package com.movauy.mova.model.user;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.movauy.mova.model.branch.Branch; // ← nueva entidad
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
  name = "user",
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_branch_username", columnNames = { "branch_id", "username" })
  }
)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "branch_id", nullable = true)
    private Branch branch;

    @Column(name = "token_version", nullable = true)
    private String tokenVersion;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
