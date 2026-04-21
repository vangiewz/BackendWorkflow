package backendworkflow.backend.models;

import java.time.LocalDateTime;

public record RegistroTiempo(
        String pasoId,
        String funcionarioId,
        String funcionarioNombre,
        LocalDateTime fechaEntrada,
        LocalDateTime fechaSalida
) {
}
