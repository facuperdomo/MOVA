package com.movauy.mova.Jwt;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.movauy.mova.model.user.Role;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    private static final String SECRET_KEY = "586E3272357538782F413F4428472B4B6250655368566B597033733676397924";
    private static final long TOKEN_EXPIRATION_TIME = 1000 * 60 * 30;

    private final UserRepository userRepository;

    public JwtService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String getToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("ver", user.getTokenVersion());

        if (user.getBranch() != null) {
            claims.put("branchId", user.getBranch().getId());
            claims.put("companyId", user.getBranch().getCompany().getId());
        }

        return generateToken(claims, user);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + TOKEN_EXPIRATION_TIME))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(Map<String, Object> extraClaims, String subject) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + TOKEN_EXPIRATION_TIME))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = getAllClaims(token);
            String usernameInToken = claims.getSubject();
            Date expiration = claims.getExpiration();

            if (!usernameInToken.equals(userDetails.getUsername())) {
                logger.warn("[JWT-SERVICE] ❌ El subject del token ({}) no coincide con UserDetails ({})", usernameInToken, userDetails.getUsername());
                return false;
            }

            if (expiration.before(new Date())) {
                logger.warn("[JWT-SERVICE] ❌ Token expirado para '{}'", usernameInToken);
                return false;
            }

            if (userDetails instanceof User user) {
                if (user.getRole() == Role.SUPERADMIN) {
                    logger.warn("[JWT-SERVICE] ✅ SUPERADMIN detectado, se omite validación de tokenVersion");
                    return true;
                }

                String tokenVer = claims.get("ver", String.class);
                String currentVer = user.getTokenVersion();

                logger.warn("[JWT-SERVICE] Verificando tokenVersion: tokenVer={}, currentVer={}", tokenVer, currentVer);

                if (tokenVer == null && currentVer == null) {
                    return true;
                }

                if (tokenVer != null && tokenVer.equals(currentVer)) {
                    return true;
                } else {
                    logger.warn("[JWT-SERVICE] ❌ tokenVersion mismatch: tokenVer={}, currentVer={} (user={})", tokenVer, currentVer, user.getUsername());
                    return false;
                }
            }

            return true;

        } catch (ExpiredJwtException ex) {
            logger.warn("[JWT-SERVICE] ⚠️ Token expirado, propagando...");
            throw ex;

        } catch (Exception e) {
            logger.warn("[JWT-SERVICE] ❌ Error validando token", e);
            return false;
        }
    }

    private Key getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims getAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Claims getAllClaimsAllowExpired(String token) {
        try {
            return getAllClaims(token);
        } catch (ExpiredJwtException ex) {
            return ex.getClaims();
        }
    }

    public <T> T getClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(getAllClaims(token));
    }

    public String getUsernameFromToken(String token) {
        return getClaim(token, Claims::getSubject); // getAllClaimsAllowExpired se usa por dentro
    }

    private Date getExpiration(String token) {
        return getClaim(token, Claims::getExpiration);
    }

    private boolean isTokenExpired(String token) {
        return getExpiration(token).before(new Date());
    }

    public <T> T getClaimAllowExpired(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(getAllClaimsAllowExpired(token));
    }
}
