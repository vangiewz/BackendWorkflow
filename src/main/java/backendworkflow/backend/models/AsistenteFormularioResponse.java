package backendworkflow.backend.models;

import java.util.Map;

public record AsistenteFormularioResponse(
        String pasoId,
        Map<String, Object> sugerencia,
        String observacion
) {
}
