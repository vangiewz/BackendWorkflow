package backendworkflow.backend.models;

public record UsuarioConectadoEvent(
        String usuarioNombre,
        String accion // "JOIN" o "LEAVE" 
) {
}
