package backendworkflow.backend.tramite.dto;

public record AsistenteFormularioRequest(
        String pasoId,
        String modo,
        String mensaje
) {
}
