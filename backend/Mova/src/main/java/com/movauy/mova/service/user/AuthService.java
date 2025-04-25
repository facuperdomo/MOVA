package com.movauy.mova.service.user;

import com.movauy.mova.Jwt.JwtService;
import com.movauy.mova.dto.AuthResponse;
import com.movauy.mova.dto.LoginRequest;
import com.movauy.mova.dto.RegisterRequest;
import com.movauy.mova.dto.UserBasicDTO;
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

        // ✅ En lugar de devolver "" como companyId, devolvemos el ID real de la empresa
        return AuthResponse.builder()
                .token(token)
                .role(user.getRole().name())
                .companyId(user.getId().toString()) // <- Este ID se usará para asociar usuarios
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

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );

        String token = jwtService.getToken(user);

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
                .mercadoPagoAccessToken(request.getMercadoPagoAccessToken())
                .enableIngredients(request.isEnableIngredients())
                .enableKitchenCommands(request.isEnableKitchenCommands())
                .build();

        userRepository.save(user);

        // ✅ Si es empresa y no tiene companyId, lo igualamos a su propio ID
        if (role == Role.COMPANY && (user.getCompanyId() == null || user.getCompanyId().isBlank())) {
            user.setCompanyId(user.getId().toString());
            userRepository.save(user); // guardamos con el companyId seteado
        }

        String effectiveCompanyId = user.getCompanyId() != null && !user.getCompanyId().isEmpty()
                ? user.getCompanyId()
                : user.getId().toString();

        return AuthResponse.builder()
                .token(jwtService.getToken(user))
                .role(user.getRole().name())
                .companyId(effectiveCompanyId)
                .build();
    }

    private void authenticateUser(String username, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );
    }

    public Long getCompanyIdFromToken(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        String username = jwtService.getUsernameFromToken(token);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        String companyIdStr = user.getCompanyId() != null && !user.getCompanyId().isEmpty()
                ? user.getCompanyId()
                : user.getId().toString();
        return Long.valueOf(companyIdStr);
    }

    public User getUserById(Long companyId) {
        return userRepository.findById(companyId.intValue())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    public void updateUser(User user) {
        userRepository.save(user);
    }

    public UserBasicDTO getUserBasicFromToken(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        String username = jwtService.getUsernameFromToken(token);

        // 1) el usuario logueado
        User me = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        // 2) la "empresa" a la que pertenece
        Long companyId = Long.valueOf(
                (me.getCompanyId() != null && !me.getCompanyId().isBlank())
                ? me.getCompanyId()
                : me.getId().toString()
        );
        User company = getUserById(companyId);

        // 3) devuelvo sólo el DTO
        return new UserBasicDTO(
                me.getId().longValue(),
                me.getUsername(),
                company.getCompanyId(),
                me.getRole().name(),
                company.isEnableIngredients(),
                company.isEnableKitchenCommands()
        );
    }

    public UserBasicDTO getUserBasicById(Long id) {
        // aquí id ya es companyId
        User company = getUserById(id);
        return new UserBasicDTO(
                company.getId(),
                company.getUsername(),
                company.getCompanyId(),
                company.getRole().name(),
                company.isEnableIngredients(),
                company.isEnableKitchenCommands()
        );
    }
}
