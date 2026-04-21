package backendworkflow.backend.models;

/**
 * DTO de respuesta para operaciones de autenticación exitosas.
 * Contiene el token JWT y datos básicos del usuario autenticado.
 */
public record AuthResponse(
        String token,
        String id,
        String email,
        String nombre,
        String rol,
        String departamentoId,
        String tipoUsuario
) {
}
