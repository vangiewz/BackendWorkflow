package backendworkflow.backend.controllers;

import backendworkflow.backend.models.PlantillaWorkflow;
import backendworkflow.backend.repositories.PlantillaWorkflowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
