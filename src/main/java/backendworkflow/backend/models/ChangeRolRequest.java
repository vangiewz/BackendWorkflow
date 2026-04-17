package backendworkflow.backend.models;

import jakarta.validation.constraints.NotBlank;

public record ChangeRolRequest(
        @NotBlank(message = "El rol es obligatorio")
        String rol
) {}
