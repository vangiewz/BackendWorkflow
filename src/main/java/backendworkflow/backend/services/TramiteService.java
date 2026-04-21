package backendworkflow.backend.services;

import backendworkflow.backend.models.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import backendworkflow.backend.repositories.PlantillaWorkflowRepository;
import backendworkflow.backend.repositories.TramiteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TramiteService {

    private final TramiteRepository tramiteRepository;
    private final PlantillaWorkflowRepository plantillaRepository;
    private final ClaudeAiService claudeAiService;
    private final ObjectMapper objectMapper;

    public TramiteService(
            TramiteRepository tramiteRepository,
            PlantillaWorkflowRepository plantillaRepository,
            ClaudeAiService claudeAiService
    ) {
        this.tramiteRepository = tramiteRepository;
        this.plantillaRepository = plantillaRepository;
        this.claudeAiService = claudeAiService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Crea un nuevo trámite a partir de una plantilla activa.
     */
    public Tramite iniciarTramite(String plantillaId, String clienteId, Map<String, Object> datosCliente) {
        PlantillaWorkflow plantilla = plantillaRepository.findById(plantillaId)
                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada"));

        if (!plantilla.isActive()) {
            throw new RuntimeException("Esta plantilla no está activa");
        }

        List<PasoWorkflow> pasos = plantilla.getPasos();
        if (pasos == null || pasos.isEmpty()) {
            throw new RuntimeException("La plantilla no tiene pasos definidos");
        }

        // El primer paso del workflow es el punto de entrada
        String primerPasoId = pasos.get(0).id();

        Tramite tramite = new Tramite();
        tramite.setPlantillaId(plantillaId);
        tramite.setNombrePlantilla(plantilla.getNombre());
        tramite.setClienteId(clienteId);
        tramite.setEstadoGlobal("PENDIENTE");
        tramite.setPasoActualId(primerPasoId);
        tramite.setDatosFormularioCliente(datosCliente != null ? datosCliente : new HashMap<>());
        tramite.setFechaCreacion(LocalDateTime.now());

        // Registrar el inicio del primer paso
        RegistroTiempo registro = new RegistroTiempo(
                primerPasoId, null, null, LocalDateTime.now(), null
        );
        tramite.getHistorialTiempos().add(registro);

        return tramiteRepository.save(tramite);
    }

    /**
     * Responde el formulario del paso actual y avanza al siguiente.
     * Para ACTIVIDAD: guarda respuestas y avanza vía siguientes["default"].
     * Para DECISION: recibe la condición elegida y avanza vía siguientes[condición].
     */
    public Tramite responderPaso(String tramiteId, String pasoId, String funcionarioId, 
                                  String funcionarioNombre, String departamentoId,
                                  Map<String, Object> respuesta, String decisionElegida) {
        StepExecutionContext context = loadAndValidateStepContext(tramiteId, pasoId, funcionarioId, departamentoId);
        Tramite tramite = context.tramite();
        PasoWorkflow pasoActual = context.pasoActual();

        // Cerrar el registro de tiempo del paso actual
        List<RegistroTiempo> historial = tramite.getHistorialTiempos();
        List<RegistroTiempo> nuevoHistorial = new ArrayList<>(historial);
        for (int i = nuevoHistorial.size() - 1; i >= 0; i--) {
            RegistroTiempo reg = nuevoHistorial.get(i);
            if (reg.pasoId().equals(pasoId) && reg.fechaSalida() == null) {
                nuevoHistorial.set(i, new RegistroTiempo(
                        reg.pasoId(), funcionarioId, funcionarioNombre,
                        reg.fechaEntrada(), LocalDateTime.now()
                ));
                break;
            }
        }
        tramite.setHistorialTiempos(nuevoHistorial);

        // SIEMPRE guardar entrada en respuestas para marcar el paso como completado.
        if (respuesta == null) {
            respuesta = new HashMap<>();
        }

        // Validar campos requeridos del formularioJson (si el paso tiene formulario)
        if (!"DECISION".equals(pasoActual.tipo()) && pasoActual.formularioJson() != null) {
            Object requiredObj = pasoActual.formularioJson().get("required");
            if (requiredObj instanceof java.util.List<?> requiredFields) {
                for (Object fieldObj : requiredFields) {
                    String fieldName = fieldObj.toString();
                    Object value = respuesta.get(fieldName);
                    if (value == null || (value instanceof String s && s.isBlank())) {
                        throw new RuntimeException("El campo '" + fieldName + "' es obligatorio.");
                    }
                }
            }
        }

        tramite.getRespuestas().put(pasoId, respuesta);

        // Determinar siguiente paso
        Map<String, String> siguientes = pasoActual.siguientes();
        String siguientePasoId = null;

        if ("DECISION".equals(pasoActual.tipo())) {
            // Para decisiones, usar la condición elegida
            if (decisionElegida == null || decisionElegida.isEmpty()) {
                throw new RuntimeException("Debe seleccionar una opción para la decisión");
            }
            siguientePasoId = siguientes != null ? siguientes.get(decisionElegida) : null;
            if (siguientePasoId == null) {
                throw new RuntimeException("Opción de decisión no válida: " + decisionElegida);
            }
            // Guardar la decisión tomada
            Map<String, Object> decisionData = new HashMap<>();
            decisionData.put("decision", decisionElegida);
            tramite.getRespuestas().put(pasoId, decisionData);
        } else {
            // Para actividades, usar "default" o la primera clave disponible
            if (siguientes != null && !siguientes.isEmpty()) {
                siguientePasoId = siguientes.getOrDefault("default", siguientes.values().iterator().next());
            }
        }

        // Avanzar o finalizar
        if (siguientePasoId == null || siguientePasoId.isEmpty()) {
            // No hay siguiente paso: finalizar trámite
            tramite.setPasoActualId(null);
            tramite.setEstadoGlobal("FINALIZADO");
            tramite.setFechaFinalizacion(LocalDateTime.now());
        } else {
            tramite.setPasoActualId(siguientePasoId);
            tramite.setEstadoGlobal("EN_PROGRESO");

            // Registrar inicio del siguiente paso
            RegistroTiempo nuevoRegistro = new RegistroTiempo(
                    siguientePasoId, null, null, LocalDateTime.now(), null
            );
            tramite.getHistorialTiempos().add(nuevoRegistro);
        }

        return tramiteRepository.save(tramite);
    }

    public AsistenteFormularioResponse asistirFormulario(
            String tramiteId,
            String pasoId,
            String modo,
            String mensaje,
            String usuarioId,
            String departamentoId
    ) {
        if (mensaje == null || mensaje.isBlank()) {
            throw new RuntimeException("Debes enviar un mensaje para solicitar ayuda de IA.");
        }

        StepExecutionContext context = loadAndValidateStepContext(tramiteId, pasoId, usuarioId, departamentoId);
        PasoWorkflow pasoActual = context.pasoActual();

        if ("DECISION".equals(pasoActual.tipo())) {
            throw new RuntimeException("La asistencia IA para autocompletar aplica solo a pasos de tipo ACTIVIDAD.");
        }

        Map<String, Object> schema = pasoActual.formularioJson();
        if (schema == null || schema.isEmpty()) {
            return new AsistenteFormularioResponse(pasoId, Map.of(), "Este paso no tiene formulario para autocompletar.");
        }

        try {
            String schemaJson = objectMapper.writeValueAsString(schema);
            String aiRaw = claudeAiService.sugerirCamposFormulario(schemaJson, mensaje, modo != null ? modo : "chat");
            String aiJson = extractJsonObject(aiRaw);

            Map<String, Object> parsed = objectMapper.readValue(aiJson, new TypeReference<Map<String, Object>>() {
            });
            Object sugerenciaObj = parsed.get("sugerencia");
            Object observacionObj = parsed.get("observacion");

            Map<String, Object> sugerencia = new HashMap<>();
            if (sugerenciaObj instanceof Map<?, ?> suggestionMap) {
                for (Map.Entry<?, ?> entry : suggestionMap.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (isSchemaFieldAllowed(schema, key)) {
                        sugerencia.put(key, entry.getValue());
                    }
                }
            }

            String observacion = observacionObj == null ? "" : String.valueOf(observacionObj);
            return new AsistenteFormularioResponse(pasoId, sugerencia, observacion);
        } catch (Exception e) {
            return new AsistenteFormularioResponse(
                    pasoId,
                    Map.of(),
                    "No se pudo autocompletar con IA en este intento. Puedes completar manualmente o intentar de nuevo."
            );
        }
    }

    public List<Tramite> getByClienteId(String clienteId) {
        return tramiteRepository.findByClienteId(clienteId);
    }

    public List<Tramite> getAll() {
        return tramiteRepository.findAll();
    }

    public Optional<Tramite> getById(String id) {
        return tramiteRepository.findById(id);
    }

    private StepExecutionContext loadAndValidateStepContext(
            String tramiteId,
            String pasoId,
            String usuarioId,
            String departamentoId
    ) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new RuntimeException("Trámite no encontrado"));

        if ("FINALIZADO".equals(tramite.getEstadoGlobal())) {
            throw new RuntimeException("Este trámite ya fue finalizado");
        }

        if (!pasoId.equals(tramite.getPasoActualId())) {
            throw new RuntimeException("Este no es el paso actual del trámite");
        }

        PlantillaWorkflow plantilla = plantillaRepository.findById(tramite.getPlantillaId())
                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada"));

        PasoWorkflow pasoActual = plantilla.getPasos().stream()
                .filter(p -> p.id().equals(pasoId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Paso no encontrado en la plantilla"));

        if (pasoActual.departamentoId() != null) {
            if (departamentoId == null || !departamentoId.equals(pasoActual.departamentoId())) {
                throw new RuntimeException("No tienes permisos para responder este paso. Pertenece a otro departamento.");
            }
        } else if (!usuarioId.equals(tramite.getClienteId())) {
            throw new RuntimeException("Solo el cliente que inició el trámite puede responder a este requerimiento.");
        }

        return new StepExecutionContext(tramite, pasoActual);
    }

    private boolean isSchemaFieldAllowed(Map<String, Object> schema, String fieldKey) {
        Object propertiesObj = schema.get("properties");
        if (!(propertiesObj instanceof Map<?, ?> props)) {
            return false;
        }
        return props.containsKey(fieldKey);
    }

    private String extractJsonObject(String aiRaw) {
        if (aiRaw == null) {
            return "{}";
        }

        int firstBrace = aiRaw.indexOf('{');
        int lastBrace = aiRaw.lastIndexOf('}');

        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return aiRaw.substring(firstBrace, lastBrace + 1);
        }

        return aiRaw;
    }

    private record StepExecutionContext(Tramite tramite, PasoWorkflow pasoActual) {
    }
}
