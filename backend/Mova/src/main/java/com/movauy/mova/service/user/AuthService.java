package com.movauy.mova.service.user;

import com.movauy.mova.Jwt.JwtService;
import com.movauy.mova.dto.AuthResponse;
import com.movauy.mova.dto.LoginRequest;
import com.movauy.mova.dto.RegisterRequest;
import com.movauy.mova.model.user.Role;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    /**
     * Login para una empresa (COMPANY)
     */
    public AuthResponse loginCompany(LoginRequest request) {
        authenticateUser(request.getUsername(), request.getPassword());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        if (user.getRole() != Role.COMPANY) {
            throw new BadCredentialsException("El usuario no tiene permisos de empresa");
        }

        String token = jwtService.getToken(user);
        String companyId = user.getCompanyId() != null ? user.getCompanyId() : user.getId().toString();

        return AuthResponse.builder()
                .token(token)
                .role(user.getRole().name())
                .companyId(companyId)
                .build();
    }

    /**
     * Login para usuarios normales y administradores
     */
    public AuthResponse loginUser(LoginRequest request) {
        authenticateUser(request.getUsername(), request.getPassword());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        if (request.getCompanyId() == null || request.getCompanyId().isEmpty()) {
            throw new BadCredentialsException("No se proporcionó el ID de la empresa");
        }

        if (user.getCompanyId() == null || !user.getCompanyId().equals(request.getCompanyId())) {
            throw new BadCredentialsException("El usuario no pertenece a la empresa indicada");
        }

        // Establecer autenticación en el contexto de seguridad
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );

        String token = jwtService.getToken(user); // ✅ Usa `getToken` en lugar de `generateToken`

        return AuthResponse.builder()
                .token(token)
                .role(user.getRole().name())
                .companyId(user.getCompanyId())
                .build();
    }

    /**
     * Registro de nuevos usuarios
     */
    public AuthResponse register(RegisterRequest request) {
        Role role;
        try {
            role = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El rol enviado no es válido. Use COMPANY, USER o ADMIN.", e);
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .companyId(request.getCompanyId())
                .build();

        userRepository.save(user);

        return AuthResponse.builder()
                .token(jwtService.getToken(user)) // ✅ Usa `getToken` en lugar de `generateToken`
                .role(user.getRole().name())
                .companyId(user.getCompanyId())
                .build();
    }

    /**
     * Método privado para autenticar usuarios
     */
    private void authenticateUser(String username, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );
    }
}
