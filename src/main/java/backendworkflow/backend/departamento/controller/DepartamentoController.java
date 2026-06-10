package backendworkflow.backend.departamento.controller;


import backendworkflow.backend.departamento.dto.CreateDepartamentoRequest;
import backendworkflow.backend.departamento.dto.UpdateDepartamentoRequest;
import backendworkflow.backend.departamento.model.Departamento;
import backendworkflow.backend.departamento.service.DepartamentoService;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Controller para la gestión de departamentos.
 */
@RestController
@RequestMapping("/api/departamentos")
public class DepartamentoController {

    private final DepartamentoService departamentoService;

    public DepartamentoController(DepartamentoService departamentoService) {
        this.departamentoService = departamentoService;
    }

    /**
     * Crea un nuevo departamento.
     * Solo accesible para usuarios con rol ADMIN.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createDepartamento(@Valid @RequestBody CreateDepartamentoRequest request) {
        try {
            Departamento departamento = departamentoService.createDepartamento(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(departamento);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lista todos los departamentos.
     * Accesible para cualquier usuario autenticado.
     */
    @GetMapping
    public ResponseEntity<List<Departamento>> getAllDepartamentos() {
        return ResponseEntity.ok(departamentoService.findAll());
    }

    /**
     * Permite a un ADMIN activar o desactivar un departamento (soft delete).
     */
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleActive(@PathVariable String id) {
        try {
            Departamento updated = departamentoService.toggleActive(id);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Permite a un ADMIN actualizar el nombre del departamento.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateDepartamento(
            @PathVariable String id,
            @Valid @RequestBody UpdateDepartamentoRequest request
    ) {
        try {
            Departamento updated = departamentoService.updateDepartamento(id, request);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Permite a un ADMIN eliminar un departamento permanentemente.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteDepartamento(@PathVariable String id) {
        try {
            departamentoService.deleteDepartamento(id);
            return ResponseEntity.ok(Map.of("message", "Departamento eliminado con éxito"));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
