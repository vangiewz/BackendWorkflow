package backendworkflow.backend.models;

public record DepartamentoMetrica(
        String departamentoId,
        String departamentoNombre,
        double promedioHoras,
        long etapasProcesadas
) {
}
