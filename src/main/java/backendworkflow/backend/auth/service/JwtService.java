package backendworkflow.backend.auth.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Servicio centralizado para generación y validación de JSON Web Tokens.
 *
 * Cada token generado contiene:
 * - sub: email del usuario
 * - id: ID del documento en MongoDB
 * - rol: el rol asignado (ADMIN, FUNCIONARIO, CLIENTE)
 * - tipoUsuario: "USUARIO" o "CLIENTE" (indica en qué colección buscar)
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /**
     * Genera un JWT con claims personalizados para identificar al dueño del token.
     *
     * @param id           ID del documento en MongoDB
     * @param email        Email del usuario (será el subject del token)
     * @param rol          Rol asignado (ADMIN, FUNCIONARIO, CLIENTE)
     * @param tipoUsuario  "USUARIO" para empleados internos, "CLIENTE" para externos
     * @return Token JWT firmado
     */
    public String generateToken(String id, String email, String rol, String tipoUsuario) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("id", id);
        extraClaims.put("rol", rol);
        extraClaims.put("tipoUsuario", tipoUsuario);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Extrae el email (subject) del token.
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extrae el tipo de usuario ("USUARIO" o "CLIENTE") del token.
     */
    public String extractTipoUsuario(String token) {
        return extractAllClaims(token).get("tipoUsuario", String.class);
    }

    /**
     * Extrae el rol del token.
     */
    public String extractRol(String token) {
        return extractAllClaims(token).get("rol", String.class);
    }

    /**
     * Extrae el ID del usuario del token.
     */
    public String extractId(String token) {
        return extractAllClaims(token).get("id", String.class);
    }

    /**
     * Valida que el token no haya expirado y que el email coincida.
     */
    public boolean isTokenValid(String token, String email) {
        final String tokenEmail = extractEmail(token);
        return (tokenEmail.equals(email)) && !isTokenExpired(token);
    }

    /**
     * Extrae un claim específico usando una función resolvente.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
