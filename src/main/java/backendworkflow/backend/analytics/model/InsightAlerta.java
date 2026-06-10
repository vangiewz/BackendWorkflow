package backendworkflow.backend.analytics.model;

public record InsightAlerta(
        String severidad,
        String titulo,
        String descripcion,
        String departamentoId,
        String funcionarioId,
        Double retrasoHoras,
        String causaProbable
) {
}
