package backendworkflow.backend.models;

public record EtapaTiempoCompacto(
        String pasoId,
        String funcionarioId,
        long minutosEtapa
) {
}
