package backendworkflow.backend.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminChangePasswordRequest(
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 6, message = "La nueva contraseña debe tener al menos 6 caracteres")
        String newPassword
) {}
