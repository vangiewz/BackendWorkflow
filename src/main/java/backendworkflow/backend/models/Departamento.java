package backendworkflow.backend.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "departamentos")
public class Departamento {

    @Id
    private String id;
    private String nombre;

    public Departamento() {
    }

    public Departamento(String nombre) {
        this.nombre = nombre;
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
}
