package backendworkflow.backend.departamento.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDepartamentoRequest(
        @NotBlank(message = "El nombre del departamento es requerido")
        String nombre,
        String descripcion
) {}
