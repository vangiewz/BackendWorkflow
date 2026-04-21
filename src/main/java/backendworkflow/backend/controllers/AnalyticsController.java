package backendworkflow.backend.controllers;

import backendworkflow.backend.models.AnalisisCuellosBotellaResponse;
import backendworkflow.backend.services.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
