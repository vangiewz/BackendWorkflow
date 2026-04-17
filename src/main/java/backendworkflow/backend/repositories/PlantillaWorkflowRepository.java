package backendworkflow.backend.repositories;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import backendworkflow.backend.models.PlantillaWorkflow;

public interface PlantillaWorkflowRepository extends MongoRepository<PlantillaWorkflow, String> {

    List<PlantillaWorkflow> findByIsActiveTrue();
}
