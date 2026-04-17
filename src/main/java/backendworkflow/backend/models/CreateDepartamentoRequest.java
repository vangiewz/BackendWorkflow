package backendworkflow.backend.models;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para la creación de departamentos.
 */
public record CreateDepartamentoRequest(
        @NotBlank(message = "El nombre del departamento es obligatorio")
        String nombre
) {
}
