package backendworkflow.backend.analytics.controller;


import backendworkflow.backend.analytics.dto.AnalisisCuellosBotellaResponse;
import backendworkflow.backend.analytics.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/cuellos-botella")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<AnalisisCuellosBotellaResponse> analizarCuellosBotella(
            @RequestParam(defaultValue = "24") double horasEsperadasPromedio
    ) {
        return ResponseEntity.ok(analyticsService.analizarCuellosBotella(horasEsperadasPromedio));
    }

    @PostMapping("/report-data")
    public ResponseEntity<List<Map<String, Object>>> getAnalyticsReportData(@RequestBody Map<String, Object> filtros) {
        try {
            List<Map<String, Object>> resultados = analyticsService.generarReporteAgrupado(filtros);
            return ResponseEntity.ok(resultados);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
