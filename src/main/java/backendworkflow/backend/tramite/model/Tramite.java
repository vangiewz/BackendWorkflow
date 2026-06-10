package backendworkflow.backend.tramite.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Document(collection = "tramites")
public class Tramite {

    @Id
    private String id;

    private String plantillaId;
    private String nombrePlantilla;
    private String clienteId;
    private String estadoGlobal; // PENDIENTE, EN_PROGRESO, FINALIZADO
    private String prioridad; // ALTA, MEDIA, BAJA
    private Boolean riesgoDemora;
    private Double tiempoEstimadoDias; // Predicción por Regresión
    private String rutaSugerida; // Generado por IA
    private Boolean esAnomalo; // Isolation Forest
    private List<String> pasosActualesIds; // IDs de los pasos activos en el grafo de la plantilla
    private Map<String, Set<String>> joinNodosAlcanzados; // ID de JOIN -> Set de IDs de branches que han llegado
    private Map<String, Object> datosFormularioCliente; // Datos iniciales del cliente
    private Map<String, Map<String, Object>> respuestas; // pasoId -> respuestas del formulario
    private List<RegistroTiempo> historialTiempos;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaFinalizacion;

    private String paymentId;
    private String invoiceUrl;
    private String clienteEmail;

    @org.springframework.data.mongodb.core.mapping.Field("documentos")
    private List<ArchivoMetadata> documentos;

    public Tramite() {
        this.pasosActualesIds = new ArrayList<>();
        this.joinNodosAlcanzados = new HashMap<>();
        this.respuestas = new HashMap<>();
        this.historialTiempos = new ArrayList<>();
        this.documentos = new ArrayList<>();
    }

    // Getters y Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlantillaId() { return plantillaId; }
    public void setPlantillaId(String plantillaId) { this.plantillaId = plantillaId; }
    public String getNombrePlantilla() { return nombrePlantilla; }
    public void setNombrePlantilla(String nombrePlantilla) { this.nombrePlantilla = nombrePlantilla; }
    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }
    public String getEstadoGlobal() { return estadoGlobal; }
    public void setEstadoGlobal(String estadoGlobal) { this.estadoGlobal = estadoGlobal; }
    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
    public Boolean getRiesgoDemora() { return riesgoDemora; }
    public void setRiesgoDemora(Boolean riesgoDemora) { this.riesgoDemora = riesgoDemora; }
    public Double getTiempoEstimadoDias() { return tiempoEstimadoDias; }
    public void setTiempoEstimadoDias(Double tiempoEstimadoDias) { this.tiempoEstimadoDias = tiempoEstimadoDias; }
    public String getRutaSugerida() { return rutaSugerida; }
    public void setRutaSugerida(String rutaSugerida) { this.rutaSugerida = rutaSugerida; }
    public Boolean getEsAnomalo() { return esAnomalo; }
    public void setEsAnomalo(Boolean esAnomalo) { this.esAnomalo = esAnomalo; }
    public List<String> getPasosActualesIds() { return pasosActualesIds; }
    public void setPasosActualesIds(List<String> pasosActualesIds) { this.pasosActualesIds = pasosActualesIds; }
    public Map<String, Set<String>> getJoinNodosAlcanzados() { return joinNodosAlcanzados; }
    public void setJoinNodosAlcanzados(Map<String, Set<String>> joinNodosAlcanzados) { this.joinNodosAlcanzados = joinNodosAlcanzados; }
    public Map<String, Object> getDatosFormularioCliente() { return datosFormularioCliente; }
    public void setDatosFormularioCliente(Map<String, Object> datosFormularioCliente) { this.datosFormularioCliente = datosFormularioCliente; }
    public Map<String, Map<String, Object>> getRespuestas() { return respuestas; }
    public void setRespuestas(Map<String, Map<String, Object>> respuestas) { this.respuestas = respuestas; }
    public List<RegistroTiempo> getHistorialTiempos() { return historialTiempos; }
    public void setHistorialTiempos(List<RegistroTiempo> historialTiempos) { this.historialTiempos = historialTiempos; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaFinalizacion() { return fechaFinalizacion; }
    public void setFechaFinalizacion(LocalDateTime fechaFinalizacion) { this.fechaFinalizacion = fechaFinalizacion; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getInvoiceUrl() { return invoiceUrl; }
    public void setInvoiceUrl(String invoiceUrl) { this.invoiceUrl = invoiceUrl; }
    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }
    public List<ArchivoMetadata> getDocumentos() { return documentos; }
    public void setDocumentos(List<ArchivoMetadata> documentos) { this.documentos = documentos; }

    // Campos efímeros guardados para Feedback Continuo
    private String aiTemaPrincipal;
    private String aiTonoCliente;
    private Boolean aiMencionaFechas;
    private String aiDepartamentoAsignado;
    private Integer aiCargaDepartamento;
    private Boolean aiEsViernes;

    public String getAiTemaPrincipal() { return aiTemaPrincipal; }
    public void setAiTemaPrincipal(String aiTemaPrincipal) { this.aiTemaPrincipal = aiTemaPrincipal; }
    public String getAiTonoCliente() { return aiTonoCliente; }
    public void setAiTonoCliente(String aiTonoCliente) { this.aiTonoCliente = aiTonoCliente; }
    public Boolean getAiMencionaFechas() { return aiMencionaFechas; }
    public void setAiMencionaFechas(Boolean aiMencionaFechas) { this.aiMencionaFechas = aiMencionaFechas; }
    public String getAiDepartamentoAsignado() { return aiDepartamentoAsignado; }
    public void setAiDepartamentoAsignado(String aiDepartamentoAsignado) { this.aiDepartamentoAsignado = aiDepartamentoAsignado; }
    public Integer getAiCargaDepartamento() { return aiCargaDepartamento; }
    public void setAiCargaDepartamento(Integer aiCargaDepartamento) { this.aiCargaDepartamento = aiCargaDepartamento; }
    public Boolean getAiEsViernes() { return aiEsViernes; }
    public void setAiEsViernes(Boolean aiEsViernes) { this.aiEsViernes = aiEsViernes; }
}
