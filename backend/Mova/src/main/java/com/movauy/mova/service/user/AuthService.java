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
                .build();

        userRepository.save(user);

        return AuthResponse.builder()
                .token(jwtService.getToken(user))
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

    /**
     * Método para extraer el companyId a partir del token JWT. Se asume que el
     * token contiene el username (subject) y se usa para buscar el usuario.
     */
    public Long getCompanyIdFromToken(String token) {
        // Si el token tiene el prefijo "Bearer ", se elimina.
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        // Usamos getUsernameFromToken del JwtService para obtener el username.
        String username = jwtService.getUsernameFromToken(token);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        // Se utiliza companyId si existe, de lo contrario se usa el id del usuario.
        String companyIdStr = user.getCompanyId() != null ? user.getCompanyId() : user.getId().toString();
        return Long.valueOf(companyIdStr);
    }

    /**
     * Método para obtener un objeto User a partir del companyId. Se convierte
     * el companyId a Integer para buscar el usuario.
     */
    public User getUserById(Long companyId) {
        return userRepository.findById(companyId.intValue())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    /**
     * Método para obtener un objeto User a partir del Id SIN TOKEN DE MERCADO
     * PAGO.
     */
    public User getSafeUserById(Long id) {
        return userRepository.findUserWithoutSensitiveData(id)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    public void updateUser(User user) {
        userRepository.save(user);
    }

    /**
     * Método para obtener un DTO con datos básicos del usuario a partir del
     * token JWT, sin disparar el desencriptado del campo
     * mercadoPagoAccessToken.
     */
    public UserBasicDTO getUserBasicFromToken(String token) {
        // Eliminar el prefijo Bearer si existe
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        String username = jwtService.getUsernameFromToken(token);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        return new UserBasicDTO(
                user.getId().longValue(),
                user.getUsername(),
                user.getCompanyId() != null ? user.getCompanyId() : user.getId().toString(),
                user.getRole().name()
        );
    }

    /**
     * Método para obtener un DTO con datos básicos del usuario a partir del ID,
     * evitando disparar la conversión o desencriptación del token de
     * MercadoPago.
     */
    public UserBasicDTO getUserBasicById(Long id) {
        // Se puede usar el método getSafeUserById() que ya tienes
        User user = getSafeUserById(id);
        return new UserBasicDTO(
                user.getId(),
                user.getUsername(),
                user.getCompanyId() != null ? user.getCompanyId() : user.getId().toString(),
                user.getRole().name()
        );
    }

}
