package backendworkflow.backend.departamento.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "departamentos")
public class Departamento {

    @Id
    private String id;
    private String nombre;

    @JsonProperty("isActive")
    private boolean isActive = true;

    public Departamento() {
    }

    public Departamento(String nombre) {
        this.nombre = nombre;
        this.isActive = true;
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

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
