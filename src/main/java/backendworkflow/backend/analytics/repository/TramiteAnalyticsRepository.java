package backendworkflow.backend.analytics.repository;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TramiteAnalyticsRepository {

    private final MongoTemplate mongoTemplate;

    public TramiteAnalyticsRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Document> aggregateCompletedTramiteStageLogs() {
        AggregationOperation matchFinalizados = context -> new Document("$match",
                new Document("estadoGlobal", "FINALIZADO")
                        .append("historialTiempos", new Document("$exists", true).append("$ne", List.of()))
        );

        AggregationOperation unwindHistorial = context -> new Document("$unwind", "$historialTiempos");

        AggregationOperation matchEtapasCerradas = context -> new Document("$match",
                new Document("historialTiempos.fechaEntrada", new Document("$ne", null))
                        .append("historialTiempos.fechaSalida", new Document("$ne", null))
        );

        AggregationOperation projectEtapas = context -> new Document("$project",
                new Document("tramiteId", "$_id")
                        .append("tipoTramite", "$nombrePlantilla")
                        .append("plantillaId", "$plantillaId")
                        .append("pasoId", "$historialTiempos.pasoId")
                        .append("funcionarioId", "$historialTiempos.funcionarioId")
                        .append("fechaEntrada", "$historialTiempos.fechaEntrada")
                        .append("fechaSalida", "$historialTiempos.fechaSalida")
        );

        AggregationOperation groupByTramite = context -> new Document("$group",
                new Document("_id", "$tramiteId")
                        .append("tipoTramite", new Document("$first", "$tipoTramite"))
                        .append("plantillaId", new Document("$first", "$plantillaId"))
                        .append("etapas", new Document("$push",
                                new Document("pasoId", "$pasoId")
                                        .append("funcionarioId", "$funcionarioId")
                                        .append("fechaEntrada", "$fechaEntrada")
                                        .append("fechaSalida", "$fechaSalida")
                        ))
        );

        AggregationOperation projectFinal = context -> new Document("$project",
                new Document("_id", 0)
                        .append("tramiteId", "$_id")
                        .append("tipoTramite", "$tipoTramite")
                        .append("plantillaId", "$plantillaId")
                        .append("etapas", "$etapas")
        );

        Aggregation aggregation = Aggregation.newAggregation(
                matchFinalizados,
                unwindHistorial,
                matchEtapasCerradas,
                projectEtapas,
                groupByTramite,
                projectFinal
        );

        return mongoTemplate
                .aggregate(aggregation, "tramites", Document.class)
                .getMappedResults();
    }
}
