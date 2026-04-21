package backendworkflow.backend.models;

public record PlanAccionItem(
        String prioridad,
        String accion,
        String objetivo,
        String plazoHoras
) {
}
