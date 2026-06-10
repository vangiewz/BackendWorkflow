package backendworkflow.backend.tramite.service;


import backendworkflow.backend.bitacora.service.BitacoraService;
import backendworkflow.backend.notificacion.service.N8nNotificationService;
import backendworkflow.backend.notificacion.service.NotificationStepService;
import backendworkflow.backend.pago.service.CoinGateService;
import backendworkflow.backend.storage.service.S3DocumentoService;
import backendworkflow.backend.tramite.dto.AsistenteFormularioResponse;
import backendworkflow.backend.tramite.model.ArchivoMetadata;
import backendworkflow.backend.tramite.model.PasoWorkflow;
import backendworkflow.backend.tramite.model.RegistroTiempo;
import backendworkflow.backend.tramite.model.Tramite;
import backendworkflow.backend.tramite.repository.TramiteRepository;
import backendworkflow.backend.usuario.model.Usuario;
import backendworkflow.backend.usuario.repository.UsuarioRepository;
import backendworkflow.backend.workflow.model.PlantillaWorkflow;
import backendworkflow.backend.workflow.repository.PlantillaWorkflowRepository;
import backendworkflow.backend.workflow.service.ClaudeAiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Service
public class TramiteService {

    private static final Logger logger = LoggerFactory.getLogger(TramiteService.class);

    private final TramiteRepository tramiteRepository;
    private final PlantillaWorkflowRepository plantillaRepository;
    private final ClaudeAiService claudeAiService;
    private final UsuarioRepository usuarioRepository;
    private final N8nNotificationService n8nNotificationService;
    private final NotificationStepService notificationStepService;
    private final CoinGateService coinGateService;
    private final S3DocumentoService s3DocumentoService;
    private final BitacoraService bitacoraService;
    private final ObjectMapper objectMapper;
    private final backendworkflow.backend.workflow.service.PredictiveRoutingClient predictiveRoutingClient;
    private final MongoTemplate mongoTemplate;

    public TramiteService(
            TramiteRepository tramiteRepository,
            PlantillaWorkflowRepository plantillaRepository,
            ClaudeAiService claudeAiService,
            UsuarioRepository usuarioRepository,
            N8nNotificationService n8nNotificationService,
            NotificationStepService notificationStepService,
            CoinGateService coinGateService,
            S3DocumentoService s3DocumentoService,
            BitacoraService bitacoraService,
            backendworkflow.backend.workflow.service.PredictiveRoutingClient predictiveRoutingClient,
            MongoTemplate mongoTemplate
    ) {
        this.tramiteRepository = tramiteRepository;
        this.plantillaRepository = plantillaRepository;
        this.claudeAiService = claudeAiService;
        this.usuarioRepository = usuarioRepository;
        this.n8nNotificationService = n8nNotificationService;
        this.notificationStepService = notificationStepService;
        this.coinGateService = coinGateService;
        this.s3DocumentoService = s3DocumentoService;
        this.bitacoraService = bitacoraService;
        this.predictiveRoutingClient = predictiveRoutingClient;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = new ObjectMapper();
    }

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

        String primerPasoId = pasos.get(0).id();

        Tramite tramite = new Tramite();
        tramite.setId(java.util.UUID.randomUUID().toString());
        tramite.setPlantillaId(plantillaId);
        tramite.setNombrePlantilla(plantilla.getNombre());
        tramite.setClienteId(clienteId);
        
        Usuario cliente = usuarioRepository.findById(clienteId).orElse(null);
        if (cliente != null) {
            tramite.setClienteEmail(cliente.getEmail());
        }

        tramite.setPasosActualesIds(new ArrayList<>());
        
        if (datosCliente == null) {
            datosCliente = new HashMap<>();
        }

        // Los archivos estáticos ahora se suben de forma inmediata (Upload on Select) a través de uploadDocumentoEstaticoInmediato.
        // Ya no se procesan en lote durante iniciarTramite.
        tramite.setDatosFormularioCliente(datosCliente);
        tramite.setFechaCreacion(LocalDateTime.now());

        Double costoBase = plantilla.getCostoBase() != null ? plantilla.getCostoBase() : 0.0;
        boolean requiresPayment = costoBase > 0;
        
        // Disparar motor de flujo (suprimiendo notificaciones de tareas si hay pago pendiente)
        avanzarPasoAutomaticamente(tramite, plantilla, "START", primerPasoId, requiresPayment);

        // Predecir Prioridad y Riesgo usando IA
        try {
            String departamentoInicial = null;
            long cargaActualDepartamento = 0;
            if (pasos != null && !pasos.isEmpty()) {
                departamentoInicial = pasos.get(0).departamentoId();
                if (departamentoInicial != null) {
                    String deptIdFinal = departamentoInicial;
                    List<String> pasosDelDepto = plantilla.getPasos().stream()
                            .filter(p -> deptIdFinal.equals(p.departamentoId()))
                            .map(p -> p.id())
                            .toList();
                    cargaActualDepartamento = tramiteRepository.countByPlantillaIdAndPasosActualesIdsIn(plantillaId, pasosDelDepto);
                }
            }
            
            java.time.DayOfWeek day = java.time.LocalDate.now().getDayOfWeek();
            boolean esViernesOFinSemana = (day == java.time.DayOfWeek.FRIDAY || day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY);

            Map<String, Object> predictResponse = predictiveRoutingClient.predictPriority(
                    tramite.getId(),
                    tramite.getPlantillaId(),
                    tramite.getNombrePlantilla(),
                    plantilla.getDescripcion(),
                    departamentoInicial,
                    cargaActualDepartamento,
                    esViernesOFinSemana,
                    tramite.getDatosFormularioCliente()
            );
            if (predictResponse != null) {
                tramite.setPrioridad((String) predictResponse.getOrDefault("prioridad", "MEDIA"));
                Object riesgoObj = predictResponse.get("riesgoDemora");
                if (riesgoObj instanceof Boolean) {
                    tramite.setRiesgoDemora((Boolean) riesgoObj);
                } else if (riesgoObj instanceof String) {
                    tramite.setRiesgoDemora(Boolean.parseBoolean((String) riesgoObj));
                } else {
                    tramite.setRiesgoDemora(false);
                }

                Object tiempoObj = predictResponse.get("tiempoEstimadoDias");
                if (tiempoObj instanceof Number) {
                    tramite.setTiempoEstimadoDias(((Number) tiempoObj).doubleValue());
                }

                Object rutaObj = predictResponse.get("rutaSugerida");
                if (rutaObj instanceof String) {
                    tramite.setRutaSugerida((String) rutaObj);
                }

                Object anomaloObj = predictResponse.get("esAnomalo");
                if (anomaloObj instanceof Boolean) {
                    tramite.setEsAnomalo((Boolean) anomaloObj);
                } else {
                    tramite.setEsAnomalo(false);
                }

                Object featuresObj = predictResponse.get("features_usadas");
                if (featuresObj instanceof Map) {
                    Map<String, Object> f = (Map<String, Object>) featuresObj;
                    tramite.setAiTemaPrincipal((String) f.get("tema_principal"));
                    tramite.setAiTonoCliente((String) f.get("tono_cliente"));
                    Object mencionaObj = f.get("menciona_fechas_limite");
                    if (mencionaObj instanceof Number) tramite.setAiMencionaFechas(((Number) mencionaObj).intValue() == 1);
                    tramite.setAiDepartamentoAsignado((String) f.get("departamento_asignado"));
                    Object cargaObj = f.get("carga_actual_departamento");
                    if (cargaObj instanceof Number) tramite.setAiCargaDepartamento(((Number) cargaObj).intValue());
                    Object viernesObj = f.get("es_viernes_o_fin_semana");
                    if (viernesObj instanceof Number) tramite.setAiEsViernes(((Number) viernesObj).intValue() == 1);
                }

            } else {
                tramite.setPrioridad("MEDIA");
                tramite.setRiesgoDemora(false);
                tramite.setEsAnomalo(false);
            }
        } catch (Exception e) {
            logger.error("Error al obtener la predicción de IA", e);
            tramite.setPrioridad("MEDIA");
            tramite.setRiesgoDemora(false);
            tramite.setEsAnomalo(false);
        }

        if (requiresPayment) {
            tramite.setEstadoGlobal("ESPERANDO_PAGO");
            Map<String, Object> invoiceData = coinGateService.crearFactura(costoBase, tramite.getId(), tramite.getNombrePlantilla());
            tramite.setPaymentId(String.valueOf(invoiceData.get("id")));
            tramite.setInvoiceUrl(String.valueOf(invoiceData.get("payment_url")));
        } else {
            if (tramite.getPasosActualesIds().isEmpty()) {
                tramite.setEstadoGlobal("FINALIZADO");
                tramite.setFechaFinalizacion(LocalDateTime.now());
            } else {
                tramite.setEstadoGlobal("PENDIENTE");
            }
            if (cliente != null) {
                n8nNotificationService.notificarTramiteGratis(tramite, cliente);
            }
        }

        if (archivos != null && !archivos.isEmpty()) {
            if (tramite.getDocumentos() == null) tramite.setDocumentos(new ArrayList<>());
            for (Map.Entry<String, org.springframework.web.multipart.MultipartFile> entry : archivos.entrySet()) {
                String campoKey = entry.getKey();
                org.springframework.web.multipart.MultipartFile file = entry.getValue();

                Map<String, String> permisosArchivo = new java.util.HashMap<>();
                try {
                    if (plantilla.getFormularioCliente() != null && plantilla.getFormularioCliente().get("properties") != null) {
                        Map<?,?> props = (Map<?,?>) plantilla.getFormularioCliente().get("properties");
                        if (props.containsKey(campoKey)) {
                            Map<?,?> fieldDef = (Map<?,?>) props.get(campoKey);
                            if (fieldDef.containsKey("permisosPorDefecto")) {
                                Object pDefObj = fieldDef.get("permisosPorDefecto");
                                if (pDefObj instanceof List) {
                                    List<?> pDefList = (List<?>) pDefObj;
                                    for (Object itemObj : pDefList) {
                                        if (itemObj instanceof Map) {
                                            Map<?,?> itemMap = (Map<?,?>) itemObj;
                                            String r = itemMap.get("role") != null ? itemMap.get("role").toString() : null;
                                            String p = itemMap.get("permission") != null ? itemMap.get("permission").toString() : null;
                                            if (r != null && p != null) {
                                                permisosArchivo.put(r, p);
                                            }
                                        }
                                    }
                                } else if (pDefObj instanceof Map) {
                                    Map<?,?> pDef = (Map<?,?>) pDefObj;
                                    for (Object k : pDef.keySet()) {
                                        permisosArchivo.put(k.toString(), pDef.get(k).toString());
                                    }
                                }
                            }
                        }
                    }
                } catch(Exception e) {
                    logger.error("Error extrayendo permisosPorDefecto en iniciarTramiteMultipart", e);
                }
                
                String originalFilename = file.getOriginalFilename();
                backendworkflow.backend.tramite.model.ArchivoMetadata metadata = null;
                try {
                    metadata = s3DocumentoService.uploadFile(
                            file,
                            clienteId,
                            tramite.getPlantillaId(),
                            tramite.getId(),
                            cliente != null ? cliente.getEmail() : "CLIENTE",
                            "CLIENTE",
                            permisosArchivo,
                            tramite,
                            false,
                            campoKey
                    );
                } catch (java.io.IOException e) {
                    logger.error("Error subiendo archivo a S3", e);
                    throw new RuntimeException("Error subiendo archivo inicial a S3: " + e.getMessage(), e);
                }
                
                // Asegurar que el departamento del primer paso pueda ver el archivo si no se le dio permiso explícito
                if (plantilla.getPasos() != null && !plantilla.getPasos().isEmpty()) {
                    String deptPrimerPaso = plantilla.getPasos().get(0).departamentoId();
                    if (deptPrimerPaso != null && !permisosArchivo.containsKey(deptPrimerPaso)) {
                        permisosArchivo.put(deptPrimerPaso, "LECTURA");
                    }
                }
                metadata.setPermisos(permisosArchivo);

                tramite.getDocumentos().add(metadata);
                if (tramite.getDatosFormularioCliente() != null) {
                    tramite.getDatosFormularioCliente().put(campoKey, metadata.getRutaS3());
                }
                
                bitacoraService.registrarAccion("DOCUMENTO_SUBIDO", cliente != null ? cliente.getEmail() : "CLIENTE", cliente != null ? cliente.getNombre() : "DESCONOCIDO", "CLIENTE", "Documento subido al iniciar: " + originalFilename + " para el campo: " + campoKey);
            }
        }

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

            try {
                PlantillaWorkflow plantilla = plantillaRepository.findById(tramite.getPlantillaId()).orElse(null);
                if (plantilla != null && plantilla.getPasos() != null && !plantilla.getPasos().isEmpty()) {
                    // Try to find the first active step
                    PasoWorkflow primerPaso = null;
                    if (tramite.getPasosActualesIds() != null && !tramite.getPasosActualesIds().isEmpty()) {
                        String primerIdActivo = tramite.getPasosActualesIds().get(0);
                        primerPaso = plantilla.getPasos().stream()
                                .filter(p -> p.id().equals(primerIdActivo))
                                .findFirst()
                                .orElse(null);
                    }
                    if (primerPaso == null) primerPaso = plantilla.getPasos().get(0);
                    
                    notificationStepService.notificarSiguientePaso(tramite, primerPaso);
                }
            } catch (Exception e) {
                // No bloquear
            }
        }
    }

    public Tramite responderPaso(String tramiteId, String pasoId, String funcionarioId, 
                                  String funcionarioNombre, String departamentoId,
                                  Map<String, Object> respuesta, String decisionElegida,
                                  Map<String, org.springframework.web.multipart.MultipartFile> archivos) {
        StepExecutionContext context = loadAndValidateStepContext(tramiteId, pasoId, funcionarioId, departamentoId);
        Tramite tramite = context.tramite();
        PasoWorkflow pasoActual = context.pasoActual();
        PlantillaWorkflow plantilla = context.plantilla();

        // 1. Cerrar el registro de tiempo
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

        if (respuesta == null) respuesta = new HashMap<>();
        // 2. Archivos estáticos ahora se gestionan mediante subida inmediata y versiones independientes.
        // Se omite el procesamiento de multipart batch aquí.

        // 3. Validar formulario
        if (!"DECISION".equals(pasoActual.tipo()) && !normalizeTipo(pasoActual.tipo()).equals("FORK") && pasoActual.formularioJson() != null) {
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

        // 4. Guardar decisión y respuesta
        String tipoNormalizado = normalizeTipo(pasoActual.tipo());
        boolean esDecisionImplicita = "ACTIVIDAD".equals(tipoNormalizado)
                && pasoActual.siguientes() != null && pasoActual.siguientes().size() > 1
                && !pasoActual.siguientes().containsKey("default");

        if ("DECISION".equals(tipoNormalizado) || esDecisionImplicita) {
            if (decisionElegida == null || decisionElegida.isEmpty()) {
                throw new RuntimeException("Debe seleccionar una opción para continuar");
            }
            Map<String, Object> decisionData = new HashMap<>();
            decisionData.put("decision", decisionElegida);
            decisionData.putAll(respuesta);
            tramite.getRespuestas().put(pasoId, decisionData);
        } else {
            tramite.getRespuestas().put(pasoId, respuesta);
        }

        // 5. Remover el paso completado
        tramite.getPasosActualesIds().remove(pasoId);

        // 6. Determinar siguientes pasos
        List<String> siguientesActivar = new ArrayList<>();
        String siguientePasoId = null;
        if ("DECISION".equals(tipoNormalizado) || esDecisionImplicita) {
            siguientePasoId = pasoActual.siguientes() != null ? pasoActual.siguientes().get(decisionElegida) : null;
            if (siguientePasoId == null) {
                throw new RuntimeException("Opción no válida: " + decisionElegida);
            }
        } else {
            siguientePasoId = resolveNextStepForActivity(pasoActual.siguientes(), respuesta, decisionElegida);
        }
        if (siguientePasoId != null && !siguientePasoId.isBlank()) {
            siguientesActivar.add(siguientePasoId);
        }

        // 7. Procesar siguientes pasos con la lógica automática de FORK/JOIN
        for (String sigPasoId : siguientesActivar) {
            avanzarPasoAutomaticamente(tramite, plantilla, pasoId, sigPasoId, false);
        }

        // 8. Evaluar finalización
        if (tramite.getPasosActualesIds().isEmpty()) {
            tramite.setEstadoGlobal("FINALIZADO");
            tramite.setFechaFinalizacion(LocalDateTime.now());
            
            // Enviar Feedback Continuo a la IA
            if (tramite.getAiTemaPrincipal() != null) {
                try {
                    long diasReales = java.time.temporal.ChronoUnit.DAYS.between(tramite.getFechaCreacion(), tramite.getFechaFinalizacion());
                    Map<String, Object> feedback = new HashMap<>();
                    feedback.put("tema_principal", tramite.getAiTemaPrincipal());
                    feedback.put("tono_cliente", tramite.getAiTonoCliente());
                    feedback.put("menciona_fechas_limite", tramite.getAiMencionaFechas() != null && tramite.getAiMencionaFechas() ? 1 : 0);
                    feedback.put("departamento_asignado", tramite.getAiDepartamentoAsignado());
                    feedback.put("carga_actual_departamento", tramite.getAiCargaDepartamento());
                    feedback.put("es_viernes_o_fin_semana", tramite.getAiEsViernes() != null && tramite.getAiEsViernes() ? 1 : 0);
                    feedback.put("prioridad", tramite.getPrioridad());
                    feedback.put("riesgo_demora", tramite.getRiesgoDemora() != null && tramite.getRiesgoDemora() ? 1 : 0);
                    feedback.put("tiempo_resolucion_dias", (double) diasReales);
                    
                    // Fire and forget asynchronous call
                    new Thread(() -> predictiveRoutingClient.sendFeedback(feedback)).start();
                } catch (Exception e) {
                    logger.error("Error al recopilar feedback de IA", e);
                }
            }
            
            usuarioRepository.findById(tramite.getClienteId()).ifPresent(usuario -> {
                n8nNotificationService.notificarTramiteFinalizado(tramite, usuario);
            });
            bitacoraService.registrarAccion("TRAMITE_FINALIZADO", "SISTEMA", funcionarioNombre, "FUNCIONARIO/SISTEMA", "Trámite finalizado exitosamente: " + tramite.getNombrePlantilla());
        } else {
            tramite.setEstadoGlobal("EN_PROGRESO");
        }

        Tramite saved = tramiteRepository.save(tramite);
        String logDetalle = "Paso respondido: " + pasoActual.nombrePaso() + " en trámite " + tramite.getNombrePlantilla();
        bitacoraService.registrarAccion("PASO_RESPONDIDO", funcionarioId != null ? funcionarioId : tramite.getClienteEmail(), funcionarioNombre, departamentoId != null ? "FUNCIONARIO" : "CLIENTE", logDetalle);

        return saved;
    }

    private void avanzarPasoAutomaticamente(Tramite tramite, PlantillaWorkflow plantilla, String pasoAnteriorId, String sigPasoId, boolean suppressNotifications) {
        if (sigPasoId == null || sigPasoId.isBlank()) return;
        PasoWorkflow sigPasoDef = plantilla.getPasos().stream().filter(p -> p.id().equals(sigPasoId)).findFirst().orElse(null);
        if (sigPasoDef == null) return;

        String tipoNorm = normalizeTipo(sigPasoDef.tipo());

        if ("JOIN".equals(tipoNorm)) {
            tramite.getJoinNodosAlcanzados().putIfAbsent(sigPasoId, new HashSet<>());
            tramite.getJoinNodosAlcanzados().get(sigPasoId).add(pasoAnteriorId);
            long incomingCount = calcularIncomingEdges(plantilla, sigPasoId);
            if (tramite.getJoinNodosAlcanzados().get(sigPasoId).size() >= incomingCount) {
                // El JOIN se completó, buscar adonde apunta y avanzar
                String despuesDeJoin = resolveNextStepForActivity(sigPasoDef.siguientes(), null, null);
                avanzarPasoAutomaticamente(tramite, plantilla, sigPasoId, despuesDeJoin, suppressNotifications);
            }
        } else if ("FORK".equals(tipoNorm)) {
            // El FORK avanza instantáneamente a todos sus caminos salientes
            if (sigPasoDef.siguientes() != null) {
                for (String branchSigId : sigPasoDef.siguientes().values()) {
                    avanzarPasoAutomaticamente(tramite, plantilla, sigPasoId, branchSigId, suppressNotifications);
                }
            }
        } else {
            // Nodo que requiere interacción humana (ACTIVIDAD, DECISION)
            // Se registra como activo y el sistema se detiene a esperar al humano
            tramite.getPasosActualesIds().add(sigPasoId);
            registrarInicioPaso(tramite, sigPasoId);
            if (!suppressNotifications) {
                notificarResponsable(tramite, plantilla, sigPasoId);
            }
        }
    }

    private void registrarInicioPaso(Tramite tramite, String pasoId) {
        tramite.getHistorialTiempos().add(new RegistroTiempo(pasoId, null, null, LocalDateTime.now(), null));
    }
    
    private void notificarResponsable(Tramite tramite, PlantillaWorkflow plantilla, String pasoId) {
        try {
            plantilla.getPasos().stream()
                    .filter(p -> p.id().equals(pasoId))
                    .findFirst()
                    .ifPresent(paso -> notificationStepService.notificarSiguientePaso(tramite, paso));
        } catch (Exception e) {
            // Ignorar
        }
    }

    private long calcularIncomingEdges(PlantillaWorkflow plantilla, String targetPasoId) {
        if (plantilla.getPasos() == null) return 0;
        return plantilla.getPasos().stream()
                .filter(p -> p.siguientes() != null && p.siguientes().containsValue(targetPasoId))
                .count();
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

        if ("DECISION".equals(pasoActual.tipo()) || "FORK".equals(normalizeTipo(pasoActual.tipo())) || "JOIN".equals(normalizeTipo(pasoActual.tipo()))) {
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

            Map<String, Object> parsed = objectMapper.readValue(aiJson, new TypeReference<Map<String, Object>>() {});
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

    public List<Tramite> obtenerDatosReporte(Map<String, Object> filtros) {
        Query query = new Query();

        if (filtros != null && !filtros.isEmpty()) {
            if (filtros.containsKey("estadoGlobal") && filtros.get("estadoGlobal") != null) {
                query.addCriteria(Criteria.where("estadoGlobal").is(filtros.get("estadoGlobal").toString()));
            }

            if (filtros.containsKey("nombrePlantilla") && filtros.get("nombrePlantilla") != null) {
                query.addCriteria(Criteria.where("nombrePlantilla").regex(filtros.get("nombrePlantilla").toString(), "i"));
            }

            if (filtros.containsKey("nombreCliente") && filtros.get("nombreCliente") != null) {
                // Buscamos usuarios cuyo nombre coincida
                String nombreCliente = filtros.get("nombreCliente").toString();
                Query userQuery = new Query(Criteria.where("nombre").regex(nombreCliente, "i"));
                List<Usuario> usuarios = mongoTemplate.find(userQuery, Usuario.class);
                List<String> idsUsuarios = usuarios.stream().map(Usuario::getId).toList();
                
                if (!idsUsuarios.isEmpty()) {
                    query.addCriteria(Criteria.where("clienteId").in(idsUsuarios));
                } else {
                    // Si no hay usuarios que coincidan, retornamos lista vacía para no hacer un full scan
                    return new ArrayList<>();
                }
            }
            
            // Filtro por departamento asignado: Esto implicaría buscar trámites cuyos pasos actuales pertenezcan al departamento.
            // Una forma simple es no filtrar aquí si es muy complejo, pero intentaremos buscar en el historial o asumir que no lo soportamos directo.
            // Para cumplir con el requerimiento, buscaremos los departamentos en el repositorio si es posible.
            // Dado que "departamentoAsignado" no está directo en trámite, no se aplicará un Criteria directo.
            
            if (filtros.containsKey("fechaInicio") || filtros.get("fechaFin") != null) {
                Criteria dateCriteria = Criteria.where("fechaCreacion");
                boolean hasDate = false;
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                
                if (filtros.containsKey("fechaInicio") && filtros.get("fechaInicio") != null) {
                    try {
                        LocalDate start = LocalDate.parse(filtros.get("fechaInicio").toString(), formatter);
                        dateCriteria.gte(start.atStartOfDay());
                        hasDate = true;
                    } catch (Exception ignored) {}
                }
                
                if (filtros.containsKey("fechaFin") && filtros.get("fechaFin") != null) {
                    try {
                        LocalDate end = LocalDate.parse(filtros.get("fechaFin").toString(), formatter);
                        dateCriteria.lte(end.atTime(LocalTime.MAX));
                        hasDate = true;
                    } catch (Exception ignored) {}
                }
                
                if (hasDate) {
                    query.addCriteria(dateCriteria);
                }
            }
        }

        List<Tramite> resultados = mongoTemplate.find(query, Tramite.class);
        
        // Post-filtro para departamento asignado, ya que es complejo hacerlo en la query si depende de la plantilla
        if (filtros != null && filtros.containsKey("departamentoAsignado") && filtros.get("departamentoAsignado") != null) {
            String depto = filtros.get("departamentoAsignado").toString().toLowerCase();
            resultados = resultados.stream().filter(t -> {
                if (t.getPasosActualesIds() == null || t.getPasosActualesIds().isEmpty()) return false;
                PlantillaWorkflow plantilla = plantillaRepository.findById(t.getPlantillaId()).orElse(null);
                if (plantilla == null) return false;
                
                return plantilla.getPasos().stream()
                        .anyMatch(p -> t.getPasosActualesIds().contains(p.id()) && 
                                       p.departamentoId() != null && 
                                       p.departamentoId().toLowerCase().contains(depto));
            }).toList();
        }

        return resultados;
    }

    public Optional<Tramite> getById(String id) {
        return tramiteRepository.findById(id);
    }

    public Optional<Tramite> findByIdAndEstadoGlobal(String id, String estadoGlobal) {
        return tramiteRepository.findByIdAndEstadoGlobal(id, estadoGlobal);
    }

    public void deleteTramite(String id) {
        tramiteRepository.deleteById(id);
        logger.info("✅ Trámite {} eliminado correctamente (pago cancelado/expirado)", id);
    }

    public synchronized ArchivoMetadata inicializarDocumentoColaborativo(String tramiteId, String campoKey, String formato, Map<String, String> permisos) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new RuntimeException("Trámite no encontrado"));

        // Buscar si ya existe un documento para este campo y devolver el de MAYOR versión
        ArchivoMetadata latestDoc = null;
        if (tramite.getDocumentos() != null) {
            for (ArchivoMetadata doc : tramite.getDocumentos()) {
                if (campoKey.equals(doc.getCampoKey()) && doc.isEsColaborativo()) {
                    if (latestDoc == null || doc.getVersion() > latestDoc.getVersion()) {
                        latestDoc = doc;
                    }
                }
            }
        }
        
        if (latestDoc != null) {
            return latestDoc;
        }

        // Si no existe, creamos uno nuevo con ID temporal
        ArchivoMetadata metadata = new ArchivoMetadata();
        metadata.setArchivoId(java.util.UUID.randomUUID().toString());
        metadata.setCampoKey(campoKey);
        metadata.setNombreOriginal("documento_" + campoKey);
        metadata.setFormato(formato != null ? formato : "WORD");
        metadata.setEsColaborativo(true);
        metadata.setVersion(1);
        metadata.setPermisos(permisos != null ? permisos : new HashMap<>());
        metadata.setRutaS3(""); // Se llenará cuando se guarde de verdad

        tramite.getDocumentos().add(metadata);
        tramiteRepository.save(tramite);

        bitacoraService.registrarAccion("DOCUMENTO_INICIALIZADO", "SISTEMA", "SISTEMA", "SISTEMA", "Documento colaborativo inicializado para campo: " + campoKey);

        return metadata;
    }

    public synchronized ArchivoMetadata uploadDocumentoEstaticoInmediato(
            String tramiteId, String pasoId, String campoKey, 
            org.springframework.web.multipart.MultipartFile file, 
            String usuarioId, String usuarioNombre, String departamentoId) {
        
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new RuntimeException("Trámite no encontrado"));

        if (tramite.getPasosActualesIds() == null || !tramite.getPasosActualesIds().contains(pasoId)) {
            throw new RuntimeException("HTTP 403: El paso actual ya no está activo. Doble Candado activado.");
        }

        int ultimaVersion = 0;
        if (tramite.getDocumentos() != null) {
            for (backendworkflow.backend.tramite.model.ArchivoMetadata doc : tramite.getDocumentos()) {
                if (campoKey.equals(doc.getCampoKey()) && !doc.isEsColaborativo()) {
                    if (doc.getVersion() > ultimaVersion) {
                        ultimaVersion = doc.getVersion();
                    }
                }
            }
        }

        int nuevaVersion = ultimaVersion + 1;

        Map<String, String> permisosArchivo = new java.util.HashMap<>();
        try {
            backendworkflow.backend.workflow.model.PlantillaWorkflow plantilla = plantillaRepository.findById(tramite.getPlantillaId()).orElse(null);
            if (plantilla != null && plantilla.getPasos() != null) {
                for (backendworkflow.backend.tramite.model.PasoWorkflow p : plantilla.getPasos()) {
                    if (p.id().equals(pasoId) && p.formularioJson() != null) {
                        Object propsObj = p.formularioJson().get("properties");
                        if (propsObj instanceof Map) {
                            Map<?, ?> properties = (Map<?, ?>) propsObj;
                            Object fieldDefObj = properties.get(campoKey);
                            if (fieldDefObj instanceof Map) {
                                Map<?, ?> fieldDef = (Map<?, ?>) fieldDefObj;
                                Object permisosObj = fieldDef.get("permisosPorDefecto");
                                if (permisosObj instanceof Map) {
                                    Map<?, ?> permisosDefecto = (Map<?, ?>) permisosObj;
                                    for (Object keyObj : permisosDefecto.keySet()) {
                                        permisosArchivo.put(keyObj.toString(), permisosDefecto.get(keyObj).toString());
                                    }
                                }
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Ignorar errores de parseo de permisos
        }

        backendworkflow.backend.tramite.model.ArchivoMetadata metadata;
        try {
            metadata = s3DocumentoService.uploadFile(
                file, tramite.getClienteId(), tramite.getPlantillaId(), tramite.getId(),
                usuarioId, departamentoId, permisosArchivo, tramite, false, campoKey
            );
        } catch (Exception e) {
            throw new RuntimeException("Error al subir documento a S3: " + e.getMessage());
        }

        metadata.setCampoKey(campoKey);
        metadata.setVersion(nuevaVersion);
        metadata.setEsColaborativo(false);

        if (tramite.getDocumentos() == null) {
            tramite.setDocumentos(new java.util.ArrayList<>());
        }
        tramite.getDocumentos().add(metadata);
        tramiteRepository.save(tramite);

        if (nuevaVersion == 1) {
            bitacoraService.registrarAccion(
                    "UPLOAD_ESTATICO",
                    "N/A",
                    usuarioNombre,
                    "N/A",
                    "Se cargó inicialmente el archivo " + metadata.getNombreOriginal() + " de forma permanente (v1)"
            );
        } else {
            bitacoraService.registrarAccion(
                    "REEMPLAZO_ESTATICO",
                    "N/A",
                    usuarioNombre,
                    "N/A",
                    "Se reemplazó la versión " + (nuevaVersion - 1) + " por la versión " + nuevaVersion + " del archivo " + metadata.getNombreOriginal()
            );
        }

        return metadata;
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

        if (tramite.getPasosActualesIds() == null || !tramite.getPasosActualesIds().contains(pasoId)) {
            throw new RuntimeException("El paso solicitado no está activo en este trámite");
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

        return new StepExecutionContext(tramite, pasoActual, plantilla);
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

        if (decisionElegida != null && !decisionElegida.isBlank()) {
            String byDecision = siguientes.get(decisionElegida);
            if (byDecision != null) return byDecision;
        }

        if (respuesta != null && !respuesta.isEmpty()) {
            for (Object value : respuesta.values()) {
                if (value == null) continue;
                String normalized = String.valueOf(value);
                String nextByExactValue = siguientes.get(normalized);
                if (nextByExactValue != null) return nextByExactValue;
            }
        }

        logger.warn("Rutas complejas sin coincidencia explícita. Usando primera ruta como fallback.");
        return siguientes.values().iterator().next();
    }

    private String normalizeTipo(String tipo) {
        if (tipo == null) return "ACTIVIDAD";
        String upper = tipo.toUpperCase().trim();
        return switch (upper) {
            case "ACTIVITY", "TASK", "ACTIVIDAD" -> "ACTIVIDAD";
            case "DECISION", "GATEWAY", "DECISIÓN" -> "DECISION";
            case "FORK", "PARALLEL", "SPLIT" -> "FORK";
            case "JOIN", "MERGE" -> "JOIN";
            default -> "ACTIVIDAD";
        };
    }

    private String extractJsonObject(String aiRaw) {
        if (aiRaw == null) return "{}";
        int firstBrace = aiRaw.indexOf('{');
        int lastBrace = aiRaw.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return aiRaw.substring(firstBrace, lastBrace + 1);
        }
        return aiRaw;
    }

    private FieldConfig extraerConfiguracionCampo(Map<String, Object> schema, String fieldKey, String departamentoPasoActual) {
        if (schema == null) return new FieldConfig(false, false, permisosPorDefectoSeguros(departamentoPasoActual), null, null);
        
        Object propertiesObj = schema.get("properties");
        if (propertiesObj instanceof Map<?, ?> props) {
            Object fieldObj = props.get(fieldKey);
            if (fieldObj instanceof Map<?, ?> fieldProps) {
                Object typeObj = fieldProps.get("type");
                String type = typeObj != null ? String.valueOf(typeObj) : "";
                
                boolean esColab = "DOCUMENTO_COLABORATIVO".equals(type);
                Map<String, String> permisosMap = new HashMap<>();
                
                Object permisosObj = fieldProps.get("permisosPorDefecto");
                if (permisosObj instanceof Map<?, ?> pMap) {
                    for (Map.Entry<?, ?> pEntry : pMap.entrySet()) {
                        permisosMap.put(String.valueOf(pEntry.getKey()), String.valueOf(pEntry.getValue()));
                    }
                }
                
                if (permisosMap.isEmpty()) {
                    permisosMap = permisosPorDefectoSeguros(departamentoPasoActual);
                }
                
                boolean isDoc = "ARCHIVO_ESTATICO".equals(type) || "DOCUMENTO_COLABORATIVO".equals(type);
                
                java.util.List<String> formatosPermitidos = new java.util.ArrayList<>();
                Object formatosObj = fieldProps.get("formatosPermitidos");
                if (formatosObj instanceof java.util.List<?> list) {
                    for (Object o : list) {
                        formatosPermitidos.add(String.valueOf(o).toLowerCase());
                    }
                }

                Integer tamanoMaximoMB = null;
                Object tamanoObj = fieldProps.get("tamanoMaximoMB");
                if (tamanoObj instanceof Number n) {
                    tamanoMaximoMB = n.intValue();
                } else if (tamanoObj instanceof String s && !s.isBlank()) {
                    try {
                        tamanoMaximoMB = Integer.parseInt(s);
                    } catch (NumberFormatException ignored) {}
                }

                // Even if not strictly marked as doc in JSON, if we received a file, apply safe default to upload it.
                return new FieldConfig(isDoc, esColab, permisosMap, formatosPermitidos, tamanoMaximoMB);
            }
        }
        return new FieldConfig(false, false, permisosPorDefectoSeguros(departamentoPasoActual), null, null);
    }

    private Map<String, String> permisosPorDefectoSeguros(String departamentoPasoActual) {
        Map<String, String> seguros = new HashMap<>();
        seguros.put("CLIENTE", "LECTURA");
        if (departamentoPasoActual != null && !departamentoPasoActual.isBlank()) {
            seguros.put(departamentoPasoActual, "LECTURA");
        }
        return seguros;
    }

    private record FieldConfig(boolean isDocument, boolean esColaborativo, Map<String, String> permisos, java.util.List<String> formatosPermitidos, Integer tamanoMaximoMB) {}

    private record StepExecutionContext(Tramite tramite, PasoWorkflow pasoActual, PlantillaWorkflow plantilla) {
    }
}
