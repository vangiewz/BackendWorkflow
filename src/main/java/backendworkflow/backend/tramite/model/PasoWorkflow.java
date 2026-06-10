package backendworkflow.backend.tramite.model;

import java.util.Map;

public record PasoWorkflow(
        String id,
        String nombrePaso,
        String tipo,
        String departamentoId,
        Map<String, Object> formularioJson,
        Map<String, String> siguientes
) {
}
