package backendworkflow.backend.analytics.model;

public record DepartamentoMetrica(
        String departamentoId,
        String departamentoNombre,
        double promedioHoras,
        long etapasProcesadas
) {
}
