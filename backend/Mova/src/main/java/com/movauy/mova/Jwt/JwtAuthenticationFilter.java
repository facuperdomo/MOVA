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
import org.springframework.http.HttpHeaders;
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
        logger.debug("[JWT-FILTER] → {} {}", method, path);

        // 🚫 Evitamos aplicar el filtro al refresh-token porque puede estar expirado
        if (path.equals("/auth/refresh-token")) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(method)
                || path.equals("/auth/loginUser")
                || path.equals("/auth/loginBranch")
                || path.equals("/auth/loginCompany")
                || path.startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.equals("/auth/register")) {
            String token = resolveToken(request);
            if (token != null) {
                try {
                    String username = jwtService.getUsernameFromToken(token);
                    UserDetails uds = userDetailsService.loadUserByUsername(username);
                    if (jwtService.isTokenValid(token, uds)) {
                        var authorities = uds.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .map(r -> r.startsWith("ROLE_") ? new SimpleGrantedAuthority(r)
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

        String token = resolveToken(request);
        if (token == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Falta token");
            return;
        }

        try {
            String username = jwtService.getUsernameFromToken(token);
            String authType = jwtService.getClaim(token, claims -> claims.get("authType", String.class));
            Long branchId = jwtService.getClaim(token, c -> c.get("branchId", Long.class));

            if (branchId != null) {
                Branch branch = authService.getBranchById(branchId);
                if (!branch.isEnabled() || !branch.getCompany().isEnabled()) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Sucursal o empresa deshabilitada");
                    return;
                }
            }

            if ("BRANCH".equals(authType)) {
                filterChain.doFilter(request, response);
                return;
            }

            UserDetails uds = userDetailsService.loadUserByUsername(username);

            if (!jwtService.isTokenValid(token, uds)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o revocado");
                return;
            }

            var authorities = uds.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(r -> r.startsWith("ROLE_") ? new SimpleGrantedAuthority(r)
                    : new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());

            var auth = new UsernamePasswordAuthenticationToken(uds, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (ExpiredJwtException ex) {
            logger.warn("⚠️ Token expirado para usuario: {}", ex.getClaims().getSubject());

            // ⛔ Bloquea todo salvo refresh-token
            if (path.equals("/auth/refresh-token")) {
                // ⚠️ Dejá pasar el request al controlador que borra el tokenVersion
                filterChain.doFilter(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expirado");
            }

            return;
        }

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
