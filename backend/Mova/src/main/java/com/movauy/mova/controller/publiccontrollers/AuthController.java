package com.movauy.mova.controller.publiccontrollers;

import com.movauy.mova.Jwt.JwtService;
import com.movauy.mova.dto.AuthResponse;
import com.movauy.mova.service.user.AuthService;
import com.movauy.mova.dto.LoginRequest;
import com.movauy.mova.dto.RegisterRequest;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador de autenticación y registro.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDetailsService userDetailsService;

    /**
     * Login para empresas.
     */
    @PostMapping("loginCompany")
    public ResponseEntity<AuthResponse> loginCompany(@RequestBody LoginRequest request) {
        AuthResponse response = authService.loginCompany(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Login para usuarios.
     */
    @PostMapping("loginUser")
    public ResponseEntity<AuthResponse> loginUser(@RequestBody LoginRequest request) {
        System.out.println("Request companyId: " + request.getCompanyId());
        AuthResponse response = authService.loginUser(request);
        System.out.println("Authentication en SecurityContext: " + SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.ok(response);
    }

    /**
     * Registro de nuevos usuarios.
     */
    @PostMapping("register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * Refresca el token JWT.
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token inválido o ausente.");
            }

            String oldToken = authHeader.substring(7);
            String username = jwtService.getUsernameFromToken(oldToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtService.isTokenValid(oldToken, userDetails)) {
                return ResponseEntity.ok(Collections.singletonMap("newToken", oldToken));
            }

            String newToken = jwtService.generateToken(userDetails);
            return ResponseEntity.ok(Collections.singletonMap("newToken", newToken));

        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("El token ha expirado.");
        } catch (MalformedJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token mal formado.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error al refrescar token.");
        }
    }
}
