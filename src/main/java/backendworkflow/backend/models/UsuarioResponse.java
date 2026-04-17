package backendworkflow.backend.models;

/**
 * DTO de respuesta para operaciones con usuarios internos.
 * Excluye el password hash por seguridad.
 */
public record UsuarioResponse(
                String id,
                String email,
                String nombre,
                String rol,
                String departamentoId,
                String telefono,
                boolean isActive) {
}
