package backendworkflow.backend.departamento.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDepartamentoRequest(
        @NotBlank(message = "El nombre del departamento es requerido")
        String nombre
) {}
