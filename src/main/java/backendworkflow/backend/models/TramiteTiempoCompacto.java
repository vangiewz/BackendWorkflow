package backendworkflow.backend.models;

import java.util.List;

public record TramiteTiempoCompacto(
        String tramiteId,
        String tipoTramite,
        long minutosTotales,
        List<EtapaTiempoCompacto> etapas
) {
}
