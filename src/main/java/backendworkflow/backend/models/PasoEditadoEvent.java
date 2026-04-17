package backendworkflow.backend.models;

import java.util.Map;

public record PasoEditadoEvent(
        String pasoId,
        String nuevoNombre,
        Map<String, Object> nuevoFormulario
) {
}
