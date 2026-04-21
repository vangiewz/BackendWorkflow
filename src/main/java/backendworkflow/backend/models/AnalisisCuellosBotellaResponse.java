package backendworkflow.backend.models;

import java.util.List;

public record AnalisisCuellosBotellaResponse(
        List<TramiteTiempoCompacto> logsCompactos,
        List<DepartamentoMetrica> metricasDepartamentos,
        List<InsightAlerta> insights,
        List<PlanAccionItem> planAccion
) {
}
