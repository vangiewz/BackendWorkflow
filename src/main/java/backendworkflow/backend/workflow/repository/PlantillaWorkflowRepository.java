package backendworkflow.backend.workflow.repository;


import backendworkflow.backend.workflow.model.PlantillaWorkflow;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlantillaWorkflowRepository extends MongoRepository<PlantillaWorkflow, String> {

    List<PlantillaWorkflow> findByIsActiveTrue();
}
