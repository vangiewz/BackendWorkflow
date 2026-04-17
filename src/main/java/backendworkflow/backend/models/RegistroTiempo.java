package backendworkflow.backend.models;

import java.time.LocalDateTime;

public record RegistroTiempo(
        Integer pasoOrden,
        String funcionarioId,
        LocalDateTime fechaEntrada,
        LocalDateTime fechaSalida
) {
}
