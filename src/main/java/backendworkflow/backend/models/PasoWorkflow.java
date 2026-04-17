package backendworkflow.backend.models;

import java.util.Map;

public record PasoWorkflow(
        Integer orden,
        String departamentoId,
        String nombrePaso,
        Map<String, Object> formularioJson
) {
}
