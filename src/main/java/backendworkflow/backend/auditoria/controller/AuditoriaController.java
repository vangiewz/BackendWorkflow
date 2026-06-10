package backendworkflow.backend.auditoria.controller;

import backendworkflow.backend.auditoria.model.RegistroAuditoria;
import backendworkflow.backend.auditoria.service.AuditoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ADMIN')")
    @GetMapping("/documental/{idCliente}")
    public ResponseEntity<List<RegistroAuditoria>> obtenerAuditoriaPorCliente(
            @PathVariable String idCliente,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String tipoEvento,
            @RequestParam(required = false) String actorId) {
        try {
            List<RegistroAuditoria> auditoria = auditoriaService.obtenerAuditoriaPorCliente(idCliente, startDate,
                    endDate, tipoEvento, actorId);
            return ResponseEntity.ok(auditoria);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ADMIN')")
    @GetMapping("/documental/recientes")
    public ResponseEntity<?> obtenerAuditoriaReciente(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String tipoEvento,
            @RequestParam(required = false) String actorId) {
        try {
            List<RegistroAuditoria> auditoria = auditoriaService.obtenerAuditoriaReciente(startDate, endDate,
                    tipoEvento, actorId);
            return ResponseEntity.ok(auditoria);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error interno: " + e.getMessage() + " | Causa: "
                    + (e.getCause() != null ? e.getCause().getMessage() : ""));
        }
    }
}
