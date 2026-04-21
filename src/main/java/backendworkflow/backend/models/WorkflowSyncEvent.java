package backendworkflow.backend.models;

import java.util.List;
import java.util.Map;

/**
 * Evento de sincronización completa del estado de un workflow.
 * Se usa para colaboración en tiempo real entre múltiples admins.
 */
public record WorkflowSyncEvent(
        String usuarioNombre,
        String nombre,
        String descripcion,
        String categoria,
        Double costoBase,
        Map<String, Object> formularioCliente,
        List<PasoWorkflow> pasos
) {
}
