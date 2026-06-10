package backendworkflow.backend.tramite.model;

import java.util.Map;

public class ArchivoMetadata {

    private String archivoId;
    private String nombreOriginal;
    private String rutaS3;
    private int version;
    private boolean esColaborativo;
    private String formato; // PDF, WORD, EXCEL, IMAGEN
    private Map<String, String> permisos;
    private String campoKey; // Para enlazarlo al campo del formulario
    private java.time.LocalDateTime fechaSubida;

    public ArchivoMetadata() {
    }

    public String getArchivoId() { return archivoId; }
    public void setArchivoId(String archivoId) { this.archivoId = archivoId; }

    public String getNombreOriginal() { return nombreOriginal; }
    public void setNombreOriginal(String nombreOriginal) { this.nombreOriginal = nombreOriginal; }

    public String getRutaS3() { return rutaS3; }
    public void setRutaS3(String rutaS3) { this.rutaS3 = rutaS3; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public boolean isEsColaborativo() { return esColaborativo; }
    public void setEsColaborativo(boolean esColaborativo) { this.esColaborativo = esColaborativo; }

    public String getFormato() { return formato; }
    public void setFormato(String formato) { this.formato = formato; }

    public Map<String, String> getPermisos() { return permisos; }
    public void setPermisos(Map<String, String> permisos) { this.permisos = permisos; }

    public String getCampoKey() { return campoKey; }
    public void setCampoKey(String campoKey) { this.campoKey = campoKey; }

    public java.time.LocalDateTime getFechaSubida() { return fechaSubida; }
    public void setFechaSubida(java.time.LocalDateTime fechaSubida) { this.fechaSubida = fechaSubida; }
}
