package backendworkflow.backend.controllers;

import backendworkflow.backend.models.PlantillaWorkflow;
import backendworkflow.backend.repositories.PlantillaWorkflowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final PlantillaWorkflowRepository plantillaWorkflowRepository;

    public WorkflowController(PlantillaWorkflowRepository plantillaWorkflowRepository) {
        this.plantillaWorkflowRepository = plantillaWorkflowRepository;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlantillaWorkflow> saveWorkflow(@RequestBody PlantillaWorkflow plantilla) {
        if (plantilla.getId() != null && plantilla.getId().isEmpty()) {
            plantilla.setId(null);
        }
        PlantillaWorkflow saved = plantillaWorkflowRepository.save(plantilla);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<PlantillaWorkflow>> getAllWorkflows() {
        return ResponseEntity.ok(plantillaWorkflowRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'CLIENTE')")
    public ResponseEntity<PlantillaWorkflow> getWorkflowById(@PathVariable String id) {
        Optional<PlantillaWorkflow> workflow = plantillaWorkflowRepository.findById(id);
        return workflow.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlantillaWorkflow> updateWorkflow(@PathVariable String id, @RequestBody PlantillaWorkflow plantilla) {
        if (!plantillaWorkflowRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        plantilla.setId(id);
        PlantillaWorkflow saved = plantillaWorkflowRepository.save(plantilla);
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlantillaWorkflow> toggleActive(@PathVariable String id) {
        Optional<PlantillaWorkflow> optWorkflow = plantillaWorkflowRepository.findById(id);
        if (optWorkflow.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PlantillaWorkflow workflow = optWorkflow.get();
        workflow.setActive(!workflow.isActive());
        PlantillaWorkflow saved = plantillaWorkflowRepository.save(workflow);
        return ResponseEntity.ok(saved);
    }
}
