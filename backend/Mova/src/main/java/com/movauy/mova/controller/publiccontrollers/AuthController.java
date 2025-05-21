package com.movauy.mova.controller.publiccontrollers;

import com.movauy.mova.Jwt.JwtAuthenticationFilter;
import com.movauy.mova.Jwt.JwtService;
import com.movauy.mova.dto.AuthResponse;
import com.movauy.mova.dto.LoginRequest;
import com.movauy.mova.dto.RegisterRequest;
import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.user.User;
import com.movauy.mova.model.user.Role;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.user.UserRepository;
import com.movauy.mova.service.user.AuthService;
import com.movauy.mova.service.user.DuplicateUsernameException;
import com.movauy.mova.service.user.UserTransactionalService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final BranchRepository branchRepository;
    private final UserTransactionalService userTransactionalService;

    /**
     * Cierra sesión del usuario. - Extrae el usuario del token. - Borra el
     * `tokenVersion` para invalidar el token actual.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Token inválido");
        }

        String token = bearerToken.substring(7);
        String username = jwtService.getUsernameFromToken(token);

        try {
            userTransactionalService.clearTokenVersionByUsername(username);
            logger.warn("✅ tokenVersion limpiado correctamente para logout de '{}'", username);
        } catch (Exception e) {
            logger.warn("❌ Error limpiando tokenVersion durante logout de '{}'", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al cerrar sesión.");
        }

        return ResponseEntity.ok(Map.of("message", "Sesión cerrada"));
    }

    /**
     * Login específico para usuarios que trabajan en una sucursal (branch). -
     * Autentica el usuario. - Verifica que esté vinculado a una sucursal. -
     * Gira la versión de su token para invalidar sesiones anteriores. - Genera
     * un nuevo JWT con los datos de la sucursal.
     */
    @PostMapping("/loginBranch")
    public ResponseEntity<AuthResponse> loginBranch(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.loginBranch(request);
            return ResponseEntity.ok(response);
        } catch (UsernameNotFoundException | BadCredentialsException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.builder().message(e.getMessage()).build());
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthResponse.builder().message("Error al iniciar sesión").build());
        }
    }

    /**
     * Login general (incluye SUPERADMIN). - Si es SUPERADMIN: genera JWT sin
     * branch ni empresa. - Si es usuario normal: valida que no tenga sesión
     * activa, gira el token, y genera el JWT con datos de sucursal y empresa.
     */
    @PostMapping("/loginUser")
    public ResponseEntity<AuthResponse> loginUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody LoginRequest request) {

        String token = null;

        // 👇 Intentamos extraer el token del header
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        User user = authService.getUserByUsername(request.getUsername());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // ✅ Login para SUPERADMIN (no requiere branch)
        if (user.getRole() == Role.SUPERADMIN) {
            String newVersion = authService.rotateTokenVersion(user);
            Map<String, Object> claims = new HashMap<>();
            claims.put("ver", newVersion);
            claims.put("role", user.getRole().name());
            claims.put("authType", "USER"); // opcional
            String jwt = jwtService.generateToken(claims, user);

            return ResponseEntity.ok(
                    AuthResponse.builder()
                            .token(jwt)
                            .role(user.getRole().name())
                            .branchId(null)
                            .companyId(null)
                            .build()
            );
        }

        // 👇 Validación de sesión activa para usuarios normales
        if (user.getTokenVersion() != null && !user.getTokenVersion().isBlank()) {
            boolean tokenSigueActivo = false;

            try {
                if (token != null && jwtService.isTokenValid(token, user)) {
                    tokenSigueActivo = true;
                    logger.warn("🛑 Token aún válido para '{}', se bloquea nuevo login", user.getUsername());
                } else {
                    logger.warn("💥 Token vencido o inválido para '{}', limpiando tokenVersion", user.getUsername());
                    userTransactionalService.clearTokenVersionByUsername(user.getUsername());
                }
            } catch (Exception e) {
                logger.warn("⚠️ Error al validar token de '{}': {}. Se limpia por precaución.", user.getUsername(), e.getMessage());
                userTransactionalService.clearTokenVersionByUsername(user.getUsername());
            }

            if (tokenSigueActivo) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(AuthResponse.builder()
                                .message("Ya existe una sesión activa con este usuario")
                                .build());
            }
        }

        // ✅ Continuar con login normal
        AuthResponse resp = authService.loginUser(request, token);
        return ResponseEntity.ok(resp);
    }

    /**
     * Registra un nuevo usuario. - Si ya existen usuarios en la base, solo el
     * SUPERADMIN puede crear más. - Delegado completamente en el AuthService.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {

        boolean hayUsuarios = userRepository.count() > 0;

        if (hayUsuarios) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null
                    || !auth.isAuthenticated()
                    || auth.getAuthorities().stream()
                            .noneMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"))) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(AuthResponse.builder()
                                .message("Sólo SUPERADMIN puede crear nuevos usuarios")
                                .build());
            }
        }

        try {
            AuthResponse resp = authService.register(request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(resp);
        } catch (DuplicateUsernameException ex) {
            // Devuelve 409 Conflict con { error, message }
            Map<String, String> body = Map.of(
                    "error", "UsuarioDuplicado",
                    "message", ex.getMessage()
            );
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(body);
        }
    }

    /**
     * Refresca el JWT si todavía es válido y no expiró. - Extrae el token viejo
     * y valida su versión. - Si está vigente, genera un nuevo token con los
     * mismos claims. - Si ya expiró, elimina la sesión activa (tokenVersion) y
     * fuerza nuevo login.
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).body("Falta token");
        }

        String token = authHeader.substring(7);
        String username;

        try {
            username = jwtService.getUsernameFromToken(token);
        } catch (ExpiredJwtException e) {
            username = e.getClaims().getSubject();
            logger.warn("⚠️ Token expirado para usuario: {}", username);
        } catch (Exception e) {
            logger.warn("❌ Token inválido en refresh: {}", e.getMessage());
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).body("Token inválido");
        }

        logger.warn("🧹 Limpiando tokenVersion de '{}'.", username);
        userTransactionalService.clearTokenVersionByUsername(username); // ✅ ESTO FUNCIONA

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        logger.warn("🔁 Intentando refrescar token de usuario: {}", username);
        String newToken = jwtService.generateToken(user);

        logger.warn("✅ Nuevo token generado para {}", username);
        return ResponseEntity.ok(Map.of("token", newToken));
    }

    @PutMapping("/update-mercadopago-key/{branchId}")
    public ResponseEntity<?> updateMercadoPagoKey(@PathVariable Long branchId,
            @RequestBody Map<String, String> payload) {
        String newKey = payload.get("accessToken");
        if (newKey == null || newKey.isBlank()) {
            return ResponseEntity.badRequest().body("El Access Token es obligatorio");
        }
        try {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));

            branch.setMercadoPagoAccessToken(newKey);
            branchRepository.save(branch);

            return ResponseEntity.ok("Access Token actualizado correctamente");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al actualizar el Access Token: " + e.getMessage());
        }
    }

    /**
     * Devuelve información básica del usuario autenticado. - Utiliza el token
     * para identificar quién es. - Devuelve su id, rol, branchId y companyId,
     * más permisos de cocina e ingredientes.
     */
    @GetMapping("/me")
    public ResponseEntity<UserBasicDTO> me(@RequestHeader("Authorization") String bearerToken) {
        UserBasicDTO dto = authService.getUserBasicFromToken(bearerToken);
        return ResponseEntity.ok(dto);
    }

    /**
     * Crea el mapa de claims (datos personalizados) que se incluirán en el JWT.
     * Agrega: versión del token, rol del usuario, branchId y companyId si
     * existen.
     */
    private Map<String, Object> buildClaims(User user, String version) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("ver", version);
        claims.put("role", user.getRole().name());
        claims.put("authType", "USER");

        if (user.getBranch() != null) {
            claims.put("branchId", user.getBranch().getId());
            claims.put("companyId", user.getBranch().getCompany().getId());
        }

        return claims;
    }
}
