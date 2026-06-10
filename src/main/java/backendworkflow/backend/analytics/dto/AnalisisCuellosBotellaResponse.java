package backendworkflow.backend.analytics.dto;


import backendworkflow.backend.analytics.model.DepartamentoMetrica;
import backendworkflow.backend.analytics.model.InsightAlerta;
import backendworkflow.backend.analytics.model.PlanAccionItem;
import backendworkflow.backend.analytics.model.TramiteTiempoCompacto;
import java.util.List;

public record AnalisisCuellosBotellaResponse(
        List<TramiteTiempoCompacto> logsCompactos,
        List<DepartamentoMetrica> metricasDepartamentos,
        List<InsightAlerta> insights,
        List<PlanAccionItem> planAccion
) {
}
