package backendworkflow.backend.models;

public record PasoMovidoEvent(
        String pasoId,
        String sourceDepartamentoId,
        String targetDepartamentoId,
        Integer newOrden
) {
}
