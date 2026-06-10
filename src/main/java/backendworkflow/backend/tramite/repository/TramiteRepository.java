package backendworkflow.backend.tramite.repository;


import backendworkflow.backend.tramite.model.Tramite;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface TramiteRepository extends MongoRepository<Tramite, String> {

    List<Tramite> findByClienteId(String clienteId);

    List<Tramite> findByPlantillaId(String plantillaId);

    List<Tramite> findByEstadoGlobal(String estadoGlobal);

    java.util.Optional<Tramite> findByIdAndEstadoGlobal(String id, String estadoGlobal);

    java.util.Optional<Tramite> findByDocumentosArchivoId(String archivoId);

    @Query("{ '$or': [ { 'pasosActualesIds': { '$in': ?0 } }, { 'historialTiempos.pasoId': { '$in': ?0 } } ] }")
    List<Tramite> findByPasosInvolucrados(List<String> pasosIds);

    long countByPlantillaIdAndPasosActualesIdsIn(String plantillaId, List<String> pasosIds);
}
