package backendworkflow.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro de autenticación JWT que intercepta cada request HTTP.
 *
 * Flujo:
 * 1. Extrae el header "Authorization: Bearer <token>"
 * 2. Parsea y valida el token con JwtService
 * 3. Extrae tipoUsuario y rol del payload
 * 4. Crea un Authentication object y lo coloca en el SecurityContextHolder
 * 5. Continúa la cadena de filtros
 *
 * Si no hay token o es inválido, el request pasa al siguiente filtro sin
 * autenticación (Spring Security decidirá si permite o rechaza).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Si no hay header Authorization o no comienza con "Bearer ", pasar de largo
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String email = jwtService.extractEmail(jwt);

            // Solo procesar si hay un email válido y no hay autenticación previa en el contexto
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Validar firma y expiración
                if (jwtService.isTokenValid(jwt, email)) {
                    final String rol = jwtService.extractRol(jwt);
                    final String tipoUsuario = jwtService.extractTipoUsuario(jwt);

                    logger.info("JWT Auth OK - email: " + email + ", rol: " + rol + ", tipoUsuario: " + tipoUsuario + ", authorities: [ROLE_" + rol + ", TIPO_" + tipoUsuario + "]");

                    // Crear authorities con el rol y el tipo de usuario
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + rol),
                            new SimpleGrantedAuthority("TIPO_" + tipoUsuario)
                    );

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(email, null, authorities);

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    logger.warn("JWT token validation failed for email: " + email);
                }
            }
        } catch (Exception e) {
            // Token inválido o expirado — no autenticar, dejar que Security rechace si es necesario
            logger.warn("JWT authentication failed: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
