package backendworkflow.backend.repositories;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import backendworkflow.backend.models.Tramite;

public interface TramiteRepository extends MongoRepository<Tramite, String> {

    List<Tramite> findByClienteId(String clienteId);

    List<Tramite> findByPlantillaId(String plantillaId);

    List<Tramite> findByEstadoGlobal(String estadoGlobal);
}
