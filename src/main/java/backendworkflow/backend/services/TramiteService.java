package backendworkflow.backend.services;

import backendworkflow.backend.models.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import backendworkflow.backend.repositories.PlantillaWorkflowRepository;
import backendworkflow.backend.repositories.TramiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TramiteService {

    private static final Logger logger = LoggerFactory.getLogger(TramiteService.class);

    private final TramiteRepository tramiteRepository;
    private final PlantillaWorkflowRepository plantillaRepository;
    private final ClaudeAiService claudeAiService;
    private final backendworkflow.backend.repositories.UsuarioRepository usuarioRepository;
    private final N8nNotificationService n8nNotificationService;
    private final NotificationStepService notificationStepService;
    private final CoinGateService coinGateService;
    private final AzureBlobStorageService azureBlobStorageService;
    private final BitacoraService bitacoraService;
    private final ObjectMapper objectMapper;

    public TramiteService(
            TramiteRepository tramiteRepository,
            PlantillaWorkflowRepository plantillaRepository,
            ClaudeAiService claudeAiService,
            backendworkflow.backend.repositories.UsuarioRepository usuarioRepository,
            N8nNotificationService n8nNotificationService,
            NotificationStepService notificationStepService,
            CoinGateService coinGateService,
            AzureBlobStorageService azureBlobStorageService,
            BitacoraService bitacoraService
    ) {
        this.tramiteRepository = tramiteRepository;
        this.plantillaRepository = plantillaRepository;
        this.claudeAiService = claudeAiService;
        this.usuarioRepository = usuarioRepository;
        this.n8nNotificationService = n8nNotificationService;
        this.notificationStepService = notificationStepService;
        this.coinGateService = coinGateService;
        this.azureBlobStorageService = azureBlobStorageService;
        this.bitacoraService = bitacoraService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Crea un nuevo trámite a partir de una plantilla activa.
     */
    public Tramite iniciarTramite(String plantillaId, String clienteId, Map<String, Object> datosCliente) {
        return iniciarTramiteMultipart(plantillaId, clienteId, datosCliente, null);
    }

    public Tramite iniciarTramiteMultipart(String plantillaId, String clienteId, Map<String, Object> datosCliente,
                                           Map<String, org.springframework.web.multipart.MultipartFile> archivos) {
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
        tramite.setId(java.util.UUID.randomUUID().toString()); // Forzar ID temprano para el order_id
        tramite.setPlantillaId(plantillaId);
        tramite.setNombrePlantilla(plantilla.getNombre());
        tramite.setClienteId(clienteId);
        
        Usuario cliente = usuarioRepository.findById(clienteId).orElse(null);
        if (cliente != null) {
            tramite.setClienteEmail(cliente.getEmail());
        }

        tramite.setPasoActualId(primerPasoId);
        if (datosCliente == null) {
            datosCliente = new HashMap<>();
        }

        if (archivos != null && !archivos.isEmpty()) {
            for (Map.Entry<String, org.springframework.web.multipart.MultipartFile> entry : archivos.entrySet()) {
                if ("datos".equals(entry.getKey())) continue;
                try {
                    String url = azureBlobStorageService.uploadFile(entry.getValue());
                    datosCliente.put(entry.getKey(), url);
                } catch (java.io.IOException e) {
                    throw new RuntimeException("Error al subir el archivo inicial: " + entry.getKey(), e);
                }
            }
        }

        tramite.setDatosFormularioCliente(datosCliente);
        tramite.setFechaCreacion(LocalDateTime.now());

        Double costoBase = plantilla.getCostoBase() != null ? plantilla.getCostoBase() : 0.0;
        
        if (costoBase > 0) {
            tramite.setEstadoGlobal("ESPERANDO_PAGO");
            Map<String, Object> invoiceData = coinGateService.crearFactura(costoBase, tramite.getId(), tramite.getNombrePlantilla());
            tramite.setPaymentId(String.valueOf(invoiceData.get("id")));
            tramite.setInvoiceUrl(String.valueOf(invoiceData.get("payment_url")));
        } else {
            tramite.setEstadoGlobal("PENDIENTE");
            if (cliente != null) {
                n8nNotificationService.notificarTramiteGratis(tramite, cliente);
            }
            // Notificar al responsable del primer paso
            notificationStepService.notificarSiguientePaso(tramite, pasos.get(0));
        }

        // Registrar el inicio del primer paso
        RegistroTiempo registro = new RegistroTiempo(
                primerPasoId, null, null, LocalDateTime.now(), null
        );
        tramite.getHistorialTiempos().add(registro);

        Tramite saved = tramiteRepository.save(tramite);
        
        bitacoraService.registrarAccion("TRAMITE_INICIADO", cliente != null ? cliente.getEmail() : "CLIENTE_NO_ENCONTRADO", cliente != null ? cliente.getNombre() : "DESCONOCIDO", "CLIENTE", "Trámite iniciado: " + plantilla.getNombre());

        return saved;
    }

    public void confirmarPago(String orderId) {
        Tramite tramite = tramiteRepository.findById(orderId).orElse(null);
        if (tramite != null && "ESPERANDO_PAGO".equals(tramite.getEstadoGlobal())) {
            tramite.setEstadoGlobal("PENDIENTE");
            tramiteRepository.save(tramite);

            usuarioRepository.findById(tramite.getClienteId()).ifPresent(cliente -> {
                n8nNotificationService.notificarTramitePagado(tramite, cliente);
            });

            // Notificar al responsable del primer paso después del pago
            try {
                PlantillaWorkflow plantilla = plantillaRepository.findById(tramite.getPlantillaId()).orElse(null);
                if (plantilla != null && plantilla.getPasos() != null && !plantilla.getPasos().isEmpty()) {
                    PasoWorkflow primerPaso = plantilla.getPasos().stream()
                            .filter(p -> p.id().equals(tramite.getPasoActualId()))
                            .findFirst()
                            .orElse(plantilla.getPasos().get(0));
                    notificationStepService.notificarSiguientePaso(tramite, primerPaso);
                }
            } catch (Exception e) {
                // No bloquear el flujo de pago por un error de notificación
            }
        }
    }

    /**
     * Responde el formulario del paso actual y avanza al siguiente.
     * Para ACTIVIDAD: guarda respuestas y avanza vía siguientes["default"].
     * Para DECISION: recibe la condición elegida y avanza vía siguientes[condición].
     */
    public Tramite responderPaso(String tramiteId, String pasoId, String funcionarioId, 
                                  String funcionarioNombre, String departamentoId,
                                  Map<String, Object> respuesta, String decisionElegida,
                                  Map<String, org.springframework.web.multipart.MultipartFile> archivos) {
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

        // Subir archivos a Azure Blob Storage si existen y mapear URLs a las respuestas
        if (archivos != null && !archivos.isEmpty()) {
            for (Map.Entry<String, org.springframework.web.multipart.MultipartFile> entry : archivos.entrySet()) {
                // El key es el parameterName enviado desde el frontend (idealmente el key del field)
                // excepto si es el de "datos" que usamos para JSON, pero request.getFileMap() solo tiene archivos.
                if ("datos".equals(entry.getKey())) continue;
                
                try {
                    String url = azureBlobStorageService.uploadFile(entry.getValue());
                    respuesta.put(entry.getKey(), url);
                } catch (java.io.IOException e) {
                    throw new RuntimeException("Error al subir el archivo: " + entry.getKey(), e);
                }
            }
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

        // Normalizar tipo (la IA puede generar "ACTIVITY" en vez de "ACTIVIDAD")
        String tipoNormalizado = normalizeTipo(pasoActual.tipo());

        // Detectar ACTIVIDAD con múltiples rutas no-default (debería ser DECISION)
        boolean esDecisionImplicita = "ACTIVIDAD".equals(tipoNormalizado)
                && siguientes != null && siguientes.size() > 1
                && !siguientes.containsKey("default");

        if ("DECISION".equals(tipoNormalizado) || esDecisionImplicita) {
            // Para decisiones (explícitas o implícitas), usar la condición elegida
            if (decisionElegida == null || decisionElegida.isEmpty()) {
                throw new RuntimeException("Debe seleccionar una opción para continuar");
            }
            siguientePasoId = siguientes != null ? siguientes.get(decisionElegida) : null;
            if (siguientePasoId == null) {
                throw new RuntimeException("Opción no válida: " + decisionElegida 
                        + ". Opciones disponibles: " + (siguientes != null ? siguientes.keySet() : "ninguna"));
            }
            // Guardar la decisión tomada
            Map<String, Object> decisionData = new HashMap<>();
            decisionData.put("decision", decisionElegida);
            if (respuesta != null && !respuesta.isEmpty()) {
                decisionData.putAll(respuesta); // Preservar respuestas de formulario si hay
            }
            tramite.getRespuestas().put(pasoId, decisionData);
        } else {
            // Para actividades lineales (default o ruta única)
            siguientePasoId = resolveNextStepForActivity(siguientes, respuesta, decisionElegida);
        }

        // Avanzar o finalizar
        if (siguientePasoId == null || siguientePasoId.isEmpty()) {
            // No hay siguiente paso: finalizar trámite
            tramite.setPasoActualId(null);
            tramite.setEstadoGlobal("FINALIZADO");
            tramite.setFechaFinalizacion(LocalDateTime.now());
            
            // Notificar al cliente asíncronamente
            usuarioRepository.findById(tramite.getClienteId()).ifPresent(usuario -> {
                n8nNotificationService.notificarTramiteFinalizado(tramite, usuario);
            });
            
            bitacoraService.registrarAccion("TRAMITE_FINALIZADO", "SISTEMA", funcionarioNombre, "FUNCIONARIO/SISTEMA", "Trámite finalizado exitosamente: " + tramite.getNombrePlantilla());
        } else {
            tramite.setPasoActualId(siguientePasoId);
            tramite.setEstadoGlobal("EN_PROGRESO");

            // Registrar inicio del siguiente paso
            RegistroTiempo nuevoRegistro = new RegistroTiempo(
                    siguientePasoId, null, null, LocalDateTime.now(), null
            );
            tramite.getHistorialTiempos().add(nuevoRegistro);

            // Notificar al responsable del siguiente paso
            try {
                PlantillaWorkflow plantilla = plantillaRepository.findById(tramite.getPlantillaId()).orElse(null);
                if (plantilla != null && plantilla.getPasos() != null) {
                    String finalSiguientePasoId = siguientePasoId;
                    plantilla.getPasos().stream()
                            .filter(p -> p.id().equals(finalSiguientePasoId))
                            .findFirst()
                            .ifPresent(paso -> notificationStepService.notificarSiguientePaso(tramite, paso));
                }
            } catch (Exception e) {
                // No bloquear el flujo del trámite por un error de notificación
            }
        }

        Tramite saved = tramiteRepository.save(tramite);
        
        String logDetalle = "Paso respondido: " + pasoActual.nombrePaso() + " en trámite " + tramite.getNombrePlantilla();
        bitacoraService.registrarAccion("PASO_RESPONDIDO", funcionarioId != null ? funcionarioId : tramite.getClienteEmail(), funcionarioNombre, departamentoId != null ? "FUNCIONARIO" : "CLIENTE", logDetalle);

        return saved;
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
        List<Tramite> tramites = tramiteRepository.findAll();
        for (Tramite t : tramites) {
            if (t.getClienteEmail() == null && t.getClienteId() != null) {
                usuarioRepository.findById(t.getClienteId()).ifPresent(cliente -> {
                    t.setClienteEmail(cliente.getEmail());
                });
            }
        }
        return tramites;
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

    private String resolveNextStepForActivity(
            Map<String, String> siguientes,
            Map<String, Object> respuesta,
            String decisionElegida
    ) {
        if (siguientes == null || siguientes.isEmpty()) {
            return null;
        }

        if (siguientes.containsKey("default")) {
            return siguientes.get("default");
        }

        if (siguientes.size() == 1) {
            return siguientes.values().iterator().next();
        }

        // Si hay decisionElegida, usarla directamente
        if (decisionElegida != null && !decisionElegida.isBlank()) {
            String byDecision = siguientes.get(decisionElegida);
            if (byDecision != null) {
                return byDecision;
            }
        }

        // Intentar coincidir con valores de respuesta
        if (respuesta != null && !respuesta.isEmpty()) {
            for (Object value : respuesta.values()) {
                if (value == null) continue;
                String normalized = String.valueOf(value);
                String nextByExactValue = siguientes.get(normalized);
                if (nextByExactValue != null) {
                    return nextByExactValue;
                }
            }
        }

        // Fallback: si solo quedan 2+ rutas sin resolver, tomar la primera como default seguro
        // Esto previene que workflows generados por IA rompan el sistema
        logger.warn("ACTIVIDAD con múltiples rutas no resolvió match. Usando primera ruta como fallback. Rutas: {}", siguientes.keySet());
        return siguientes.values().iterator().next();
    }

    /**
     * Normaliza el tipo de paso. La IA puede generar variaciones como "ACTIVITY", "activity", etc.
     */
    private String normalizeTipo(String tipo) {
        if (tipo == null) return "ACTIVIDAD";
        String upper = tipo.toUpperCase().trim();
        return switch (upper) {
            case "ACTIVITY", "TASK", "ACTIVIDAD" -> "ACTIVIDAD";
            case "DECISION", "GATEWAY", "DECISIÓN" -> "DECISION";
            default -> "ACTIVIDAD";
        };
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
