package backendworkflow.backend.auth.dto;

public record AuthResponse(
                String token,
                String id,
                String email,
                String nombre,
                String rol,
                String departamentoId,
                String tipoUsuario) {
}
