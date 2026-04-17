package backendworkflow.backend.models;

import jakarta.validation.constraints.NotBlank;

public record UpdateDepartamentoRequest(
        @NotBlank(message = "El nombre del departamento es requerido")
        String nombre
) {}
