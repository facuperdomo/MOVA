package com.movauy.mova.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import org.springframework.core.annotation.Order;

/**
 * Permite acceso a /api/print/jobs/next y /api/print/jobs/{id}/ack
 * solo si X-Bridge-Token coincide con el secreto configurado.
 */
@Component
@Order(100)
public class BridgeTokenFilter extends OncePerRequestFilter {

    @Value("${printbridge.secret}")
    private String bridgeSecret;

    @Override
    protected void doFilterInternal(
        HttpServletRequest req,
        HttpServletResponse res,
        FilterChain chain
    ) throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean isNextJob = HttpMethod.GET.matches(req.getMethod())
                         && "/api/print/jobs/next".equals(path);
        boolean isAck    = HttpMethod.POST.matches(req.getMethod())
                         && path.matches("/api/print/jobs/\\d+/ack");

        if (isNextJob || isAck) {
            String token = req.getHeader("X-Bridge-Token");
            if (bridgeSecret.equals(token)) {
                chain.doFilter(req, res); // autorizado
            } else {
                res.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid bridge token");
            }
        } else {
            // rutas ajenas al bridge, sigue el flujo normal
            chain.doFilter(req, res);
        }
    }
}
