package backendworkflow.backend.auditoria.service;

import backendworkflow.backend.auditoria.model.RegistroAuditoria;
import backendworkflow.backend.departamento.repository.DepartamentoRepository;
import backendworkflow.backend.usuario.repository.UsuarioRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Service
public class AuditoriaService {

    public static final String DOCUMENTO_SUBIDO = "DOCUMENTO_SUBIDO";
    public static final String DOCUMENTO_LEIDO_DESCARGADO = "DOCUMENTO_LEIDO_DESCARGADO";
    public static final String DOCUMENTO_EDITADO_COLABORATIVO = "DOCUMENTO_EDITADO_COLABORATIVO";

    private final DynamoDbTable<RegistroAuditoria> auditoriaTable;
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;

    public AuditoriaService(DynamoDbClient dynamoDbClient, UsuarioRepository usuarioRepository, DepartamentoRepository departamentoRepository) {
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        this.auditoriaTable = enhancedClient.table("AuditoriaDocumental", TableSchema.fromBean(RegistroAuditoria.class));
        this.usuarioRepository = usuarioRepository;
        this.departamentoRepository = departamentoRepository;
    }

    @Async
    public void registrarEvento(String clienteId, String tipoEvento, String usuarioActorId, 
                                String departamentoActor, String tramiteId, 
                                String archivoId, String descripcionDetallada) {
        try {
            RegistroAuditoria registro = new RegistroAuditoria();
            registro.setPk("CLIENTE#" + clienteId);
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            registro.setSk("TIMESTAMP#" + timestamp);
            registro.setGsi1pk("GLOBAL"); // Set constante para el GSI
            registro.setTipoEvento(tipoEvento);

            String actorName = usuarioActorId;
            String actorEmail = "";
            if (usuarioActorId != null) {
                if (usuarioActorId.equals("ADMIN")) {
                    actorName = "Administrador del Sistema";
                    actorEmail = "admin@sistema.local";
                } else {
                    java.util.Optional<backendworkflow.backend.usuario.model.Usuario> uOpt = usuarioRepository.findById(usuarioActorId);
                    if (uOpt.isEmpty()) {
                        uOpt = usuarioRepository.findByEmail(usuarioActorId);
                    }
                    
                    if (uOpt.isPresent()) {
                        actorName = uOpt.get().getNombre();
                        actorEmail = uOpt.get().getEmail();
                    } else if (usuarioActorId.equals(clienteId)) {
                        actorName = "Cliente Externo";
                    } else {
                        actorName = usuarioActorId; // fallback
                    }
                }
            }
            registro.setUsuarioActorId(actorName);
            registro.setActorNombre(actorName);
            registro.setActorEmail(actorEmail);
            
            String deptoName = departamentoActor;
            if (departamentoActor != null) {
                deptoName = departamentoRepository.findById(departamentoActor)
                            .map(d -> d.getNombre())
                            .orElse(departamentoActor);
            } else if (usuarioActorId != null && usuarioActorId.equals(clienteId)) {
                deptoName = "CLIENTE";
            }
            registro.setDepartamentoActor(deptoName != null ? deptoName : "N/A");

            registro.setTramiteId(tramiteId);
            registro.setArchivoId(archivoId);
            
            // Limpiar la descripción para que sea más legible si contiene rutas S3 largas
            String descLimpia = descripcionDetallada;
            if (descLimpia != null && descLimpia.contains("repositorios_clientes/")) {
                descLimpia = descLimpia.replaceAll("repositorios_clientes/[^/]+/[^/]+/[^/]+/", "");
            }
            registro.setDescripcionDetallada(descLimpia);

            auditoriaTable.putItem(registro);
        } catch (Exception e) {
            System.err.println("Error asíncrono al guardar auditoría en DynamoDB: " + e.getMessage());
        }
    }

    public List<RegistroAuditoria> obtenerAuditoriaPorCliente(String clienteId, String startDate, String endDate, String tipoEvento, String actorId) {
        Key.Builder keyBuilder = Key.builder().partitionValue("CLIENTE#" + clienteId);
        QueryConditional queryConditional = buildDateRangeQueryConditional(keyBuilder, startDate, endDate);
        
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // false para ordenar de más reciente a más antiguo (Sort Key descendente)
                .limit(200); // Límite razonable

        if (tipoEvento != null && !tipoEvento.isEmpty()) {
            Expression filterExpression = Expression.builder()
                    .expression("tipoEvento = :tipoEvento")
                    .putExpressionValue(":tipoEvento", AttributeValue.builder().s(tipoEvento).build())
                    .build();
            requestBuilder.filterExpression(filterExpression);
        }

        return auditoriaTable.query(requestBuilder.build())
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(r -> {
                    if (actorId == null || actorId.isEmpty()) return true;
                    String[] parts = actorId.split("\\|");
                    String targetName = parts[0].toLowerCase();
                    String targetEmail = parts.length > 1 ? parts[1].toLowerCase() : "";
                    
                    String storedActor = r.getUsuarioActorId() != null ? r.getUsuarioActorId().toLowerCase() : "";
                    String storedNombre = r.getActorNombre() != null ? r.getActorNombre().toLowerCase() : "";
                    String storedEmail = r.getActorEmail() != null ? r.getActorEmail().toLowerCase() : "";
                    
                    boolean matchLegacy = !storedActor.isEmpty() && (storedActor.contains(targetName) || (!targetEmail.isEmpty() && storedActor.contains(targetEmail)));
                    boolean matchNew = (!storedNombre.isEmpty() && storedNombre.contains(targetName)) || (!storedEmail.isEmpty() && storedEmail.contains(targetEmail)) || (!storedEmail.isEmpty() && storedEmail.contains(targetName));
                    
                    return matchLegacy || matchNew;
                })
                .collect(Collectors.toList());
    }

    public List<RegistroAuditoria> obtenerAuditoriaReciente(String startDate, String endDate, String tipoEvento, String actorId) {
        DynamoDbIndex<RegistroAuditoria> globalIndex = auditoriaTable.index("Global-Index");

        Key.Builder keyBuilder = Key.builder().partitionValue("GLOBAL");
        QueryConditional queryConditional = buildDateRangeQueryConditional(keyBuilder, startDate, endDate);

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false); // Descendente

        if (tipoEvento != null && !tipoEvento.isEmpty()) {
            Expression filterExpression = Expression.builder()
                    .expression("tipoEvento = :tipoEvento")
                    .putExpressionValue(":tipoEvento", AttributeValue.builder().s(tipoEvento).build())
                    .build();
            requestBuilder.filterExpression(filterExpression);
        }

        return globalIndex.query(requestBuilder.build())
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(r -> {
                    if (actorId == null || actorId.isEmpty()) return true;
                    String[] parts = actorId.split("\\|");
                    String targetName = parts[0].toLowerCase();
                    String targetEmail = parts.length > 1 ? parts[1].toLowerCase() : "";
                    
                    String storedActor = r.getUsuarioActorId() != null ? r.getUsuarioActorId().toLowerCase() : "";
                    String storedNombre = r.getActorNombre() != null ? r.getActorNombre().toLowerCase() : "";
                    String storedEmail = r.getActorEmail() != null ? r.getActorEmail().toLowerCase() : "";
                    
                    boolean matchLegacy = !storedActor.isEmpty() && (storedActor.contains(targetName) || (!targetEmail.isEmpty() && storedActor.contains(targetEmail)));
                    boolean matchNew = (!storedNombre.isEmpty() && storedNombre.contains(targetName)) || (!storedEmail.isEmpty() && storedEmail.contains(targetEmail)) || (!storedEmail.isEmpty() && storedEmail.contains(targetName));
                    
                    return matchLegacy || matchNew;
                })
                .limit(30)
                .collect(Collectors.toList());
    }

    private QueryConditional buildDateRangeQueryConditional(Key.Builder keyBuilder, String startDate, String endDate) {
        if (startDate != null && endDate != null) {
            return QueryConditional.sortBetween(
                keyBuilder.sortValue("TIMESTAMP#" + startDate).build(),
                Key.builder().partitionValue(keyBuilder.build().partitionKeyValue()).sortValue("TIMESTAMP#" + endDate + "Z").build()
            );
        } else if (startDate != null) {
            return QueryConditional.sortGreaterThanOrEqualTo(keyBuilder.sortValue("TIMESTAMP#" + startDate).build());
        } else if (endDate != null) {
            return QueryConditional.sortLessThanOrEqualTo(keyBuilder.sortValue("TIMESTAMP#" + endDate + "Z").build());
        } else {
            return QueryConditional.keyEqualTo(keyBuilder.build());
        }
    }
}
