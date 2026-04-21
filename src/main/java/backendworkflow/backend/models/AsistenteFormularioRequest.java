package backendworkflow.backend.models;

public record AsistenteFormularioRequest(
        String pasoId,
        String modo,
        String mensaje
) {
}
