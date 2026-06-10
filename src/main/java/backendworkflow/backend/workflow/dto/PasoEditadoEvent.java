package backendworkflow.backend.workflow.dto;


import backendworkflow.backend.tramite.model.PasoWorkflow;
public record PasoEditadoEvent(
        String usuarioNombre,
        PasoWorkflow pasoModificado
) {
}
