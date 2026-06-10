package backendworkflow.backend.auditoria.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class RegistroAuditoria {

    private String pk; // CLIENTE#{idCliente}
    private String sk; // TIMESTAMP#{fechaHoraISO}
    private String gsi1pk; // Constante "GLOBAL" para GSI
    
    private String tipoEvento;
    private String usuarioActorId;
    private String departamentoActor;
    private String tramiteId;
    private String archivoId;
    private String descripcionDetallada;
    private String actorNombre;
    private String actorEmail;

    public RegistroAuditoria() {
    }

    @DynamoDbPartitionKey
    @software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute("PK")
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    @software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute("SK")
    @software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey(indexNames = "Global-Index")
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    @software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey(indexNames = "Global-Index")
    @software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute("gsi1pk")
    public String getGsi1pk() { return gsi1pk; }
    public void setGsi1pk(String gsi1pk) { this.gsi1pk = gsi1pk; }

    public String getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(String tipoEvento) { this.tipoEvento = tipoEvento; }

    public String getUsuarioActorId() { return usuarioActorId; }
    public void setUsuarioActorId(String usuarioActorId) { this.usuarioActorId = usuarioActorId; }

    public String getDepartamentoActor() { return departamentoActor; }
    public void setDepartamentoActor(String departamentoActor) { this.departamentoActor = departamentoActor; }

    public String getTramiteId() { return tramiteId; }
    public void setTramiteId(String tramiteId) { this.tramiteId = tramiteId; }

    public String getArchivoId() { return archivoId; }
    public void setArchivoId(String archivoId) { this.archivoId = archivoId; }

    public String getDescripcionDetallada() { return descripcionDetallada; }
    public void setDescripcionDetallada(String descripcionDetallada) { this.descripcionDetallada = descripcionDetallada; }

    public String getActorNombre() { return actorNombre; }
    public void setActorNombre(String actorNombre) { this.actorNombre = actorNombre; }

    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
}
