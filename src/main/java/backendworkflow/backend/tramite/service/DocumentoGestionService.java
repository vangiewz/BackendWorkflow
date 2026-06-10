package backendworkflow.backend.tramite.service;

import backendworkflow.backend.tramite.dto.DocumentoGestionDTO;
import backendworkflow.backend.tramite.model.ArchivoMetadata;
import backendworkflow.backend.tramite.model.RegistroTiempo;
import backendworkflow.backend.tramite.model.Tramite;
import backendworkflow.backend.tramite.repository.TramiteRepository;
import backendworkflow.backend.usuario.model.Usuario;
import backendworkflow.backend.usuario.repository.UsuarioRepository;
import backendworkflow.backend.tramite.model.PasoWorkflow;
import backendworkflow.backend.workflow.model.PlantillaWorkflow;
import backendworkflow.backend.workflow.repository.PlantillaWorkflowRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentoGestionService {

    private final TramiteRepository tramiteRepository;
    private final PlantillaWorkflowRepository plantillaRepository;
    private final UsuarioRepository usuarioRepository;

    public DocumentoGestionService(TramiteRepository tramiteRepository, 
                                   PlantillaWorkflowRepository plantillaRepository,
                                   UsuarioRepository usuarioRepository) {
        this.tramiteRepository = tramiteRepository;
        this.plantillaRepository = plantillaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<DocumentoGestionDTO> obtenerDocumentos(Usuario usuario, String tipo) {
        boolean isAdmin = "ADMIN".equals(usuario.getRol());
        String userDept = usuario.getDepartamentoId();

        List<PlantillaWorkflow> plantillas = plantillaRepository.findAll();
        Map<String, PlantillaWorkflow> plantillasMap = plantillas.stream()
                .collect(Collectors.toMap(PlantillaWorkflow::getId, p -> p));

        List<Tramite> tramites;
        if (isAdmin) {
            tramites = tramiteRepository.findAll();
        } else {
            // Se traen todos los trámites para evaluar los permisos archivo por archivo en memoria
            // Esto asegura que si un departamento tiene permiso para ver un archivo, 
            // pueda verlo incluso si el trámite aún no llega a su paso.
            tramites = tramiteRepository.findAll();
        }

        // Optimización: Extraer los IDs de clientes únicos y traer sus nombres
        Set<String> clienteIds = tramites.stream().map(Tramite::getClienteId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> nombresClientes = new HashMap<>();
        if (!clienteIds.isEmpty()) {
            for (Usuario cli : usuarioRepository.findAllById(clienteIds)) {
                nombresClientes.put(cli.getId(), cli.getNombre());
            }
        }

        List<DocumentoGestionDTO> resultados = new ArrayList<>();

        for (Tramite t : tramites) {
            PlantillaWorkflow plantilla = plantillasMap.get(t.getPlantillaId());
            if (plantilla == null || t.getDocumentos() == null) continue;

            String clienteNombre = nombresClientes.get(t.getClienteId());
            if (clienteNombre == null || clienteNombre.trim().isEmpty()) {
                clienteNombre = (t.getClienteEmail() != null && !t.getClienteEmail().trim().isEmpty()) 
                                ? t.getClienteEmail() 
                                : "Cliente Desconocido";
            }

            // Agrupar documentos del trámite por campoKey
            Map<String, List<ArchivoMetadata>> docsPorCampo = t.getDocumentos().stream()
                    .collect(Collectors.groupingBy(doc -> doc.getCampoKey() != null ? doc.getCampoKey() : "UNKNOWN"));

            for (Map.Entry<String, List<ArchivoMetadata>> entry : docsPorCampo.entrySet()) {
                List<ArchivoMetadata> versiones = entry.getValue();
                if (versiones.isEmpty()) continue;

                // Ordenar por versión descendente (la mayor es la actual)
                versiones.sort((a, b) -> Integer.compare(b.getVersion(), a.getVersion()));
                
                ArchivoMetadata latest = versiones.get(0);
                List<ArchivoMetadata> historial = versiones.size() > 1 ? versiones.subList(1, versiones.size()) : new ArrayList<>();

                PasoWorkflow pasoDoc = encontrarPasoDeDocumento(plantilla, latest.getCampoKey());
                if (pasoDoc == null) {
                    if (t.getPasosActualesIds() != null && !t.getPasosActualesIds().isEmpty()) {
                        pasoDoc = getPasoById(plantilla, t.getPasosActualesIds().get(0));
                    }
                }
                
                if (pasoDoc == null) continue;

                boolean isActive = t.getPasosActualesIds() != null && t.getPasosActualesIds().contains(pasoDoc.id());

                if ("PENDIENTES".equalsIgnoreCase(tipo) && !isActive) continue;
                if ("HISTORICO".equalsIgnoreCase(tipo) && isActive) continue;

                // Validar permisos de visibilidad y edición
                boolean editable = false;
                if (!isAdmin) {
                    if (latest.getPermisos() == null || latest.getPermisos().isEmpty()) {
                        // Fallback temporal si no hay permisos guardados
                    } else {
                        String permiso = latest.getPermisos().get(userDept);
                        if (permiso == null) {
                            continue; // No tiene permiso para ver este campo/archivo
                        }
                        if (isActive && ("EDICION".equals(permiso) || "AMBOS".equals(permiso))) {
                            editable = true;
                        }
                    }
                } else {
                    editable = isActive;
                }

                String tipoCampo = getTipoCampo(plantilla, latest.getCampoKey());
                boolean actualEsColaborativo = latest.isEsColaborativo();
                
                System.out.println("DEBUG - Documento: " + latest.getNombreOriginal() + " | CampoKey: " + latest.getCampoKey() + " | TipoCampo: " + tipoCampo + " | OriginalEsColab: " + latest.isEsColaborativo());
                
                if ("ARCHIVO_ESTATICO".equals(tipoCampo)) {
                    actualEsColaborativo = false; // Fuerza a ser estático si el esquema lo define así
                } else if (!"DOCUMENTO_COLABORATIVO".equals(tipoCampo)) {
                    // Si por algún motivo no es ni colaborativo ni estático explícito, 
                    // se asume que no es colaborativo a menos que tenga extensión de word/excel.
                    if (!"WORD".equals(latest.getFormato()) && !"EXCEL".equals(latest.getFormato())) {
                        actualEsColaborativo = false;
                    }
                }

                java.time.LocalDateTime fechaAMostrar = latest.getFechaSubida() != null ? latest.getFechaSubida() : t.getFechaCreacion();

                DocumentoGestionDTO dto = new DocumentoGestionDTO(
                        latest.getArchivoId(),
                        latest.getNombreOriginal(),
                        latest.getRutaS3(),
                        t.getId(),
                        plantilla.getNombre(),
                        pasoDoc.id(),
                        pasoDoc.nombrePaso(),
                        fechaAMostrar, 
                        t.getFechaCreacion(),
                        pasoDoc.departamentoId(),
                        editable,
                        latest.getFormato(),
                        actualEsColaborativo
                );
                dto.setCampoKey(latest.getCampoKey());
                
                dto.setClienteId(t.getClienteId());
                dto.setClienteNombre(clienteNombre);
                dto.setClienteEmail(t.getClienteEmail());
                dto.setCampoKey(entry.getKey());
                dto.setHistorialVersiones(historial);
                
                resultados.add(dto);
            }
        }
        return resultados;
    }

    private PasoWorkflow getPasoById(PlantillaWorkflow plantilla, String pasoId) {
        if (plantilla.getPasos() == null) return null;
        for (PasoWorkflow p : plantilla.getPasos()) {
            if (p.id().equals(pasoId)) return p;
        }
        return null;
    }

    private PasoWorkflow encontrarPasoDeDocumento(PlantillaWorkflow plantilla, String campoKey) {
        if (campoKey == null || plantilla.getPasos() == null) return null;
        for (PasoWorkflow p : plantilla.getPasos()) {
            if (p.formularioJson() != null) {
                Object props = p.formularioJson().get("properties");
                if (props instanceof Map<?, ?> m && m.containsKey(campoKey)) {
                    return p;
                }
            }
        }
        // Buscar en formulario de cliente
        if (plantilla.getFormularioCliente() != null) {
            Object props = plantilla.getFormularioCliente().get("properties");
            if (props instanceof Map<?, ?> m && m.containsKey(campoKey)) {
                if (!plantilla.getPasos().isEmpty()) return plantilla.getPasos().get(0);
            }
        }
        return null;
    }

    private String getTipoCampo(PlantillaWorkflow plantilla, String campoKey) {
        if (campoKey == null) return null;
        if (plantilla.getPasos() != null) {
            for (PasoWorkflow p : plantilla.getPasos()) {
                if (p.formularioJson() != null) {
                    Object props = p.formularioJson().get("properties");
                    if (props instanceof Map<?, ?> m && m.containsKey(campoKey)) {
                        Object fieldDef = m.get(campoKey);
                        if (fieldDef instanceof Map<?, ?> fMap) {
                            return (String) fMap.get("type");
                        }
                    }
                }
            }
        }
        if (plantilla.getFormularioCliente() != null) {
            Object props = plantilla.getFormularioCliente().get("properties");
            if (props instanceof Map<?, ?> m && m.containsKey(campoKey)) {
                Object fieldDef = m.get(campoKey);
                if (fieldDef instanceof Map<?, ?> fMap) {
                    return (String) fMap.get("type");
                }
            }
        }
        return null;
    }
}
