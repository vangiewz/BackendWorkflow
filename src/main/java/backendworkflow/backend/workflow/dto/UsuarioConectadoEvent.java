package backendworkflow.backend.workflow.dto;

public record UsuarioConectadoEvent(
        String usuarioNombre,
        String accion
) {
}
