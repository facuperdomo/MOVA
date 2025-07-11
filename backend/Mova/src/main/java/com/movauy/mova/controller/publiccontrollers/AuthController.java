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
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
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
        String username;
        try {
            // Intenta extraer sujeto de un token aún válido
            username = jwtService.getUsernameFromToken(token);
        } catch (ExpiredJwtException ex) {
            // Si expiró, usamos los claims caducados para sacar el subject
            username = ex.getClaims().getSubject();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("No se pudo procesar el token");
        }

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
    /**
     * Login general (incluye SUPERADMIN). - Autentica credenciales. - Si
     * force=true: borra sesión anterior (tokenVersion). - Para SUPERADMIN:
     * genera JWT sin branch. - Para usuario normal: valida única sesión (a
     * menos que force=true), gira tokenVersion y genera JWT.
     */
    @PostMapping("/loginUser")
    public ResponseEntity<AuthResponse> loginUser(
            @RequestParam(value = "forzarLogin", required = false, defaultValue = "false") boolean forzarLogin,
            @RequestBody LoginRequest request) {

        // 1) Autenticar credenciales
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        User user = authService.getUserByUsername(request.getUsername());

        // Prepara siempre el userId
        Long userId = user.getId();
        logger.info("🔑 loginUser para username='{}' resolved userId={}", request.getUsername(), userId);
        
        // 2) SUPERADMIN saltea la restricción de “sesión única”
        if (user.getRole() == Role.SUPERADMIN) {
            String newVersion = authService.rotateTokenVersion(user);
            Map<String, Object> claims = new HashMap<>();
            claims.put("ver", newVersion);
            claims.put("role", user.getRole().name());
            claims.put("authType", "USER");
            claims.put("userId", userId);
            String jwt = jwtService.generateToken(claims, user);
            return ResponseEntity.ok(
                    AuthResponse.builder()
                            .token(jwt)
                            .role(user.getRole().name())
                            .branchId(null)
                            .companyId(null)
                            .userId(userId)
                            .build()
            );
        }

        // 3) Si piden forzar login, invalido la sesión anterior
        if (forzarLogin) {
            userTransactionalService.clearTokenVersionByUsername(user.getUsername());
            logger.warn("🔄 Sesión forzada: tokenVersion limpiado para '{}'", user.getUsername());
        }

        // 4) Para usuarios normales, rechazamos el login si ya hay tokenVersion activo (y no forzado)
        if (!forzarLogin && user.getTokenVersion() != null && !user.getTokenVersion().isBlank()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(AuthResponse.builder()
                            .message("Ya hay una sesión activa con este usuario")
                            .build());
        }

        // 5) Rotar la versión, generar el JWT y devolverlo
        String newVersion = authService.rotateTokenVersion(user);
        Map<String, Object> claims = new HashMap<>();
        claims.put("ver", newVersion);
        claims.put("role", user.getRole().name());
        claims.put("authType", "USER");
        if (user.getBranch() != null) {
            claims.put("branchId", user.getBranch().getId());
            claims.put("companyId", user.getBranch().getCompany().getId());
        }
        String jwt = jwtService.generateToken(claims, user);

        return ResponseEntity.ok(
                AuthResponse.builder()
                        .token(jwt)
                        .role(user.getRole().name())
                        .branchId(user.getBranch() != null ? user.getBranch().getId() : null)
                        .companyId(user.getBranch() != null ? user.getBranch().getCompany().getId() : null)
                        .build()
        );
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
    public ResponseEntity<?> refreshToken(HttpServletRequest req) {
        String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Falta token");
        }
        String token = auth.substring(7);
        String username;
        try {
            username = jwtService.getUsernameFromToken(token);
        } catch (ExpiredJwtException e) {
            username = e.getClaims().getSubject();
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Token inválido");
        }

        // YA no hace falta distinguir expirado o no: siempre generas nuevo
        String newToken = jwtService.generateToken(
                jwtService.getAllClaimsAllowExpired(token),
                username
        );
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

    @GetMapping
    public List<UserBasicDTO> listAllUsers() {
        return userRepository.findAll().stream()
                .map(u -> {
                    UserBasicDTO dto = new UserBasicDTO();
                    dto.setId(u.getId());
                    dto.setUsername(u.getUsername());
                    dto.setBranchId(u.getBranch() != null ? u.getBranch().getId() : null);
                    dto.setCompanyId(u.getBranch() != null && u.getBranch().getCompany() != null
                            ? u.getBranch().getCompany().getId()
                            : null);
                    dto.setRole(u.getRole().name());
                    // si no usas estos flags en la UI, déjalos false
                    dto.setEnableIngredients(false);
                    dto.setEnableKitchenCommands(false);
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
