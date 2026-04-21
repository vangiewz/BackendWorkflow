package backendworkflow.backend.models;

import java.util.Map;

public record PasoWorkflow(
        String id,
        String tipo,
        String departamentoId,
        String nombrePaso,
        Map<String, Object> formularioJson,
        Map<String, String> siguientes
) {
}
