package backendworkflow.backend.workflow.dto;


import backendworkflow.backend.tramite.model.PasoWorkflow;
public record PasoMovidoEvent(
        String usuarioNombre,
        PasoWorkflow pasoMovido
) {
}
