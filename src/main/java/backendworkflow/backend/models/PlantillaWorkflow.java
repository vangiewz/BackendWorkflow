package backendworkflow.backend.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "plantillaworkflow")
public class PlantillaWorkflow {

    @Id
    private String id;

    private String nombre;
    private String descripcion;
    private boolean isActive;
    private String categoria; // INTERNO, EXTERNO
    private Double costoBase;
    private java.util.Map<String, Object> formularioCliente;
    private List<PasoWorkflow> pasos;

    public PlantillaWorkflow() {
    }

    // Getters y Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    @JsonProperty("isActive")
    public boolean isActive() {
        return isActive;
    }

    @JsonProperty("isActive")
    public void setActive(boolean active) {
        isActive = active;
    }

    public List<PasoWorkflow> getPasos() {
        return pasos;
    }

    public void setPasos(List<PasoWorkflow> pasos) {
        this.pasos = pasos;
    }

    public java.util.Map<String, Object> getFormularioCliente() {
        return formularioCliente;
    }

    public void setFormularioCliente(java.util.Map<String, Object> formularioCliente) {
        this.formularioCliente = formularioCliente;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public Double getCostoBase() {
        return costoBase;
    }

    public void setCostoBase(Double costoBase) {
        this.costoBase = costoBase;
    }
}
