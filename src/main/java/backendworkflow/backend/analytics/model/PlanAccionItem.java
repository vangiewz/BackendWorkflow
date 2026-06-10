package backendworkflow.backend.analytics.model;

public record PlanAccionItem(
        String prioridad,
        String accion,
        String objetivo,
        String plazoHoras
) {
}
