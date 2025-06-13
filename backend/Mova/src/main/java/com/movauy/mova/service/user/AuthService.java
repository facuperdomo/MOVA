package com.movauy.mova.service.user;

import com.movauy.mova.Jwt.JwtAuthenticationFilter;
import com.movauy.mova.Jwt.JwtService;
import com.movauy.mova.dto.AuthResponse;
import com.movauy.mova.dto.LoginRequest;
import com.movauy.mova.dto.RegisterRequest;
import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.model.user.Role;
import com.movauy.mova.model.user.User;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private UserTransactionalService userTransactionalService;

    public AuthResponse loginBranch(LoginRequest request) {
        Branch branch = branchRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Sucursal no encontrada"));

        if (!passwordEncoder.matches(request.getPassword(), branch.getPassword())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("branchId", branch.getId());
        claims.put("companyId", branch.getCompany().getId());
        claims.put("authType", "BRANCH"); // ✅ útil para distinguir este tipo de token

        String token = jwtService.generateToken(claims, branch.getUsername());

        return AuthResponse.builder()
                .token(token)
                .authType("BRANCH")
                .branchId(branch.getId())
                .companyId(branch.getCompany().getId())
                .build();
    }

    public AuthResponse loginUser(LoginRequest request, String token) {
        authenticate(request.getUsername(), request.getPassword());

        User user = getUserByUsername(request.getUsername());

        if (user.getRole() != Role.SUPERADMIN) {
            if (request.getBranchId() == null
                    || user.getBranch() == null
                    || !user.getBranch().getId().equals(request.getBranchId())) {
                throw new BadCredentialsException("El usuario no pertenece a la sucursal indicada");
            }
        }

        // ⚠️ Validamos si hay sesión activa real o solo quedó colgado
        if (user.getTokenVersion() != null && !user.getTokenVersion().isBlank()) {
            boolean tokenSigueActivo = false;

            try {
                if (token != null && jwtService.isTokenValid(token, user)) {
                    tokenSigueActivo = true;
                    logger.warn("🛑 Token aún válido para '{}', se bloquea nuevo login", user.getUsername());
                } else {
                    logger.warn("💥 Token vencido o no presente para '{}', limpiando tokenVersion", user.getUsername());
                    userTransactionalService.clearTokenVersionByUsername(user.getUsername());
                }
            } catch (Exception e) {
                logger.warn("⚠️ Error al validar token activo: {}. Se limpia por precaución.", e.getMessage());
                userTransactionalService.clearTokenVersionByUsername(user.getUsername());
            }

            if (tokenSigueActivo) {
                return AuthResponse.builder()
                        .message("Ya existe una sesión activa con este usuario")
                        .build();
            }
        }

        String newVersion = rotateTokenVersion(user);

        Map<String, Object> claims = new HashMap<>();
        claims.put("ver", newVersion);
        claims.put("role", user.getRole().name());
        claims.put("authType", "USER");
        claims.put("branchId", user.getBranch() != null ? user.getBranch().getId() : null);
        claims.put("companyId", user.getBranch() != null ? user.getBranch().getCompany().getId() : null);

        String newToken = jwtService.generateToken(claims, user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );

        return AuthResponse.builder()
                .token(newToken)
                .role(user.getRole().name())
                .branchId(user.getBranch() != null ? user.getBranch().getId() : null)
                .companyId(user.getBranch() != null ? user.getBranch().getCompany().getId() : null)
                .build();
    }

    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        Long branchId = request.getBranchId();
        Role role = Role.valueOf(request.getRole().toUpperCase());

        // 1) Comprueba duplicados
        if (branchId != null && userRepository.existsByUsernameAndBranch_Id(username, branchId)) {
            throw new DuplicateUsernameException(
                    "Ya existe un usuario con ese nombre en esta sucursal"
            );
        }

        // 2) Crea y guarda
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .tokenVersion(null)
                .build();

        if (branchId != null) {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada con ID: " + branchId));
            user.setBranch(branch);
        }

        userRepository.save(user);

        // 3) Arma respuesta sin token
        return AuthResponse.builder()
                .token(null)
                .role(user.getRole().name())
                .branchId(user.getBranch() != null ? user.getBranch().getId() : null)
                .companyId(user.getBranch() != null ? user.getBranch().getCompany().getId() : null)
                .build();
    }

    private void authenticate(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    }

    private String cleanToken(String token) {
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    public Long getCompanyIdFromToken(String token) {
        String username = jwtService.getUsernameFromToken(cleanToken(token));
        return getUserByUsername(username).getBranch().getCompany().getId();
    }

    public Long getBranchIdFromToken(String token) {
        return getUserBasicFromToken(token).getBranchId();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
    }

    public Branch getBranchById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada con ID: " + id));
    }

    public UserBasicDTO getUserBasicFromToken(String token) {
        String username = jwtService.getUsernameFromToken(cleanToken(token));
        User me = getUserByUsername(username);

        Long branchId = me.getBranch() != null ? me.getBranch().getId() : null;
        Long companyId = me.getBranch() != null ? me.getBranch().getCompany().getId() : null;

        return new UserBasicDTO(
                me.getId(),
                me.getUsername(),
                branchId,
                companyId,
                me.getRole().name(),
                me.getBranch() != null && me.getBranch().isEnableIngredients(),
                me.getBranch() != null && me.getBranch().isEnableKitchenCommands()
        );
    }

    public UserBasicDTO getUserBasicById(Long id) {
        User user = getUserById(id);
        Long branchId = user.getBranch() != null ? user.getBranch().getId() : null;
        Long companyId = user.getBranch() != null ? user.getBranch().getCompany().getId() : null;

        return new UserBasicDTO(
                user.getId(),
                user.getUsername(),
                branchId,
                companyId,
                user.getRole().name(),
                user.getBranch() != null && user.getBranch().isEnableIngredients(),
                user.getBranch() != null && user.getBranch().isEnableKitchenCommands()
        );
    }

    /**
     * Genera una nueva versión de token (UUID) para el usuario, la guarda en la
     * base de datos y la devuelve. Sirve para invalidar sesiones anteriores.
     */
    @Transactional
    public String rotateTokenVersion(User user) {
        String newVersion = UUID.randomUUID().toString();

        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        managedUser.setTokenVersion(newVersion);

        userRepository.save(managedUser); // ← 🔧 Esto es lo que faltaba

        logger.warn("🔁 Persistiendo nueva tokenVersion: {}", newVersion);

        return newVersion;
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public class DuplicateUsernameException extends RuntimeException {

        public DuplicateUsernameException(String message) {
            super(message);
        }
    }

    /**
     * Extrae la entidad User a partir del token JWT que llega en el header
     * "Authorization". Espera recibir el header completo, p. ej. "Bearer
     * eyJhbGciOiJI…".
     *
     * @param rawToken El contenido completo del header "Authorization".
     * @return La entidad User correspondiente al username embebido en el token.
     * @throws ResponseStatusException HttpStatus.UNAUTHORIZED si el token no es
     * válido o no corresponde a ningún usuario.
     */
    public User getUserEntityFromToken(String rawToken) {
        // 1) Limpiar el prefijo "Bearer " (si viene) para obtener solo el JWT
        String token = cleanToken(rawToken);

        // 2) Obtener el username que quedó embebido en el JWT
        String username;
        try {
            username = jwtService.getUsernameFromToken(token);
        } catch (Exception e) {
            // Si el token no es válido, volvemos 401
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token no válido");
        }

        // 3) Cargar el User desde la base de datos
        User usuario = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Usuario no encontrado en el token"
        ));

        // 4) (Opcional) podrías validar aquí, por ejemplo, si el token coincide con su tokenVersion,
        //    o si su sesión aún es válida según la lógica de tu aplicación.
        //    Por simplicidad asumimos que ya está validado en otros filtros (JwtAuthenticationFilter).
        return usuario;
    }
}
