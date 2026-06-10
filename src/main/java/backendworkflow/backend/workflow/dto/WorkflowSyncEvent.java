package backendworkflow.backend.workflow.dto;


import backendworkflow.backend.workflow.model.PlantillaWorkflow;
public record WorkflowSyncEvent(
        String usuarioNombre,
        PlantillaWorkflow workflowCompleto
) {
}
