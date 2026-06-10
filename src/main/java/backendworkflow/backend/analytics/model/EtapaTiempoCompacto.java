package backendworkflow.backend.analytics.model;

public record EtapaTiempoCompacto(
        String pasoId,
        String funcionarioId,
        long minutosEtapa
) {
}
