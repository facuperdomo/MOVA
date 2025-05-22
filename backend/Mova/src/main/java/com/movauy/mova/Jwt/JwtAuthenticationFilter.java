package com.movauy.mova.Jwt;

import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.service.user.AuthService;
import com.movauy.mova.service.user.UserTransactionalService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Order(200)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    private AuthService authService;

    @Autowired
    public void setAuthService(@Lazy AuthService authService) {
        this.authService = authService;
    }

    private UserTransactionalService userTransactionalService;

    @Autowired
    public void setUserTransactionalService(@Lazy UserTransactionalService userTransactionalService) {
        this.userTransactionalService = userTransactionalService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // ** SALTAR JWT para webhook **
        if (path.startsWith("/api/webhooks/mercadopago")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        if (path.startsWith("/api/mercadopago/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 0) Si viene del bridge, saltamos TODO el filtro JWT
        boolean isNextJob = HttpMethod.GET.matches(method)
                && "/api/print/jobs/next".equals(path);
        boolean isAck = HttpMethod.POST.matches(method)
                && path.matches("/api/print/jobs/\\d+/ack");
        if (isNextJob || isAck) {
            filterChain.doFilter(request, response);
            return;
        }

        logger.debug("[JWT-FILTER] → {} {}", method, path);

        // 1) refresh-token (expirado o no) siempre permitido
        if ("/auth/refresh-token".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2) Rutas públicas que no requieren token
        if ("OPTIONS".equalsIgnoreCase(method)
                || "/auth/loginUser".equals(path)
                || "/auth/loginBranch".equals(path)
                || "/auth/loginCompany".equals(path)
                || path.startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3) Registro de usuario permitido incluso con token expirado
        if ("/auth/register".equals(path)) {
            String maybeToken = resolveToken(request);
            if (maybeToken != null) {
                try {
                    String username = jwtService.getUsernameFromToken(maybeToken);
                    UserDetails uds = userDetailsService.loadUserByUsername(username);
                    if (jwtService.isTokenValid(maybeToken, uds)) {
                        var authorities = uds.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .map(r -> r.startsWith("ROLE_")
                                ? new SimpleGrantedAuthority(r)
                                : new SimpleGrantedAuthority("ROLE_" + r))
                                .collect(Collectors.toList());
                        var auth = new UsernamePasswordAuthenticationToken(uds, null, authorities);
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (ExpiredJwtException ignored) {
                }
            }
            filterChain.doFilter(request, response);
            return;
        }

        // 4) Extraer Bearer token
        String token = resolveToken(request);
        if (token == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Falta token");
            return;
        }

        try {
            // 5) Validar token y claims
            String username = jwtService.getUsernameFromToken(token);
            String authType = jwtService.getClaim(token, c -> c.get("authType", String.class));
            Long branchId = jwtService.getClaim(token, c -> c.get("branchId", Long.class));

            // 6) Si es un token BRANCH, lo dejamos pasar directamente
            if ("BRANCH".equals(authType)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 7) Verificar sucursal/empresa
            if (branchId != null) {
                Branch branch = authService.getBranchById(branchId);
                if (!branch.isEnabled() || !branch.getCompany().isEnabled()) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Sucursal o empresa deshabilitada");
                    return;
                }
            }

            // 8) Cargar UserDetails y validar firma/expiración
            UserDetails uds = userDetailsService.loadUserByUsername(username);
            if (!jwtService.isTokenValid(token, uds)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o revocado");
                return;
            }

            // 9) Autenticación correcta: guardar en contexto
            var authorities = uds.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(r -> r.startsWith("ROLE_")
                    ? new SimpleGrantedAuthority(r)
                    : new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());
            var auth = new UsernamePasswordAuthenticationToken(uds, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (ExpiredJwtException ex) {
            logger.warn("⚠️ Token expirado para usuario: {}", ex.getClaims().getSubject());
            if ("/auth/refresh-token".equals(path)) {
                filterChain.doFilter(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expirado");
            }
            return;
        }

        // 10) Continuar cadena
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        String token = request.getParameter("token");
        return StringUtils.hasText(token) ? token : null;
    }
}
