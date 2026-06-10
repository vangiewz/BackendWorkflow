package backendworkflow.backend.storage.service;

import backendworkflow.backend.auditoria.service.AuditoriaService;
import backendworkflow.backend.tramite.model.ArchivoMetadata;
import backendworkflow.backend.tramite.model.Tramite;
import backendworkflow.backend.tramite.repository.TramiteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class S3DocumentoService {

    private final S3Client s3Client;
    private final AuditoriaService auditoriaService;
    private final TramiteRepository tramiteRepository;

    @Value("${aws.s3.bucket:gestion-documental-workflow-987456}")
    private String bucketName;

    public S3DocumentoService(S3Client s3Client, AuditoriaService auditoriaService, TramiteRepository tramiteRepository) {
        this.s3Client = s3Client;
        this.auditoriaService = auditoriaService;
        this.tramiteRepository = tramiteRepository;
    }

    public ArchivoMetadata uploadFile(MultipartFile file, String idCliente, String idPolitica, String idTramite, 
                                      String usuarioSubida, String departamentoSubida, Map<String, String> permisos, 
                                      Tramite tramiteActual, boolean esColaborativo, String campoKey) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null) {
            filename = filename.replaceAll("[^a-zA-Z0-9.-]", "_");
        }
        return internalUpload(
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()),
                filename, idCliente, idPolitica, idTramite, usuarioSubida, departamentoSubida, 
                permisos, tramiteActual, esColaborativo, "Subido archivo estático (campo: " + campoKey + ") sanitizado como: ", campoKey
        );
    }

    public ArchivoMetadata uploadVirtualFile(byte[] content, String originalFilename, String idCliente, String idPolitica, String idTramite, 
                                      String usuarioSubida, String departamentoSubida, Map<String, String> permisos, 
                                      Tramite tramiteActual, boolean esColaborativo, String campoKey) throws IOException {
        if (originalFilename != null) {
            originalFilename = originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
        }
        return internalUpload(
                RequestBody.fromBytes(content),
                originalFilename, idCliente, idPolitica, idTramite, usuarioSubida, departamentoSubida, 
                permisos, tramiteActual, esColaborativo, "Documento virtual guardado (campo: " + campoKey + ") como: ", campoKey
        );
    }

    private ArchivoMetadata internalUpload(RequestBody requestBody, String originalFilename, String idCliente, String idPolitica, String idTramite, 
                                      String usuarioSubida, String departamentoSubida, Map<String, String> permisos, 
                                      Tramite tramiteActual, boolean esColaborativo, String logMensajeBase, String campoKey) throws IOException {
        if (originalFilename == null) {
            originalFilename = "file_" + System.currentTimeMillis();
        }

        int version = 1;
        if (tramiteActual.getDocumentos() != null) {
            String finalOriginalFilename = originalFilename;
            version = tramiteActual.getDocumentos().stream()
                    .filter(doc -> doc.getNombreOriginal().equals(finalOriginalFilename))
                    .mapToInt(ArchivoMetadata::getVersion)
                    .max()
                    .orElse(0) + 1;
        }

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }

        String baseName = originalFilename;
        if (dotIndex > 0) {
            baseName = originalFilename.substring(0, dotIndex);
        }

        String fileNameWithVersion = baseName + "_v" + version + extension;
        String s3Key = "repositorios_clientes/" + idCliente + "/" + idPolitica + "/" + idTramite + "/" + fileNameWithVersion;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        s3Client.putObject(putObjectRequest, requestBody);

        String archivoId = UUID.randomUUID().toString();

        ArchivoMetadata metadata = new ArchivoMetadata();
        metadata.setArchivoId(archivoId);
        metadata.setNombreOriginal(originalFilename);
        metadata.setRutaS3(s3Key);
        metadata.setVersion(version);
        metadata.setEsColaborativo(esColaborativo);
        metadata.setFormato(determinarFormato(originalFilename));
        metadata.setPermisos(permisos);
        metadata.setFechaSubida(java.time.LocalDateTime.now());

        auditoriaService.registrarEvento(
                idCliente, 
                AuditoriaService.DOCUMENTO_SUBIDO, 
                usuarioSubida, 
                departamentoSubida, 
                idTramite, 
                archivoId, 
                logMensajeBase + s3Key
        );

        return metadata;
    }

    public InputStream descargarDocumento(String archivoId, String usuarioSolicitante, String departamentoSolicitante, String rolSolicitante) {
        Optional<Tramite> tramiteOpt = tramiteRepository.findByDocumentosArchivoId(archivoId);
        if (tramiteOpt.isEmpty()) {
            throw new RuntimeException("Archivo no encontrado en ningún trámite.");
        }

        Tramite tramite = tramiteOpt.get();
        ArchivoMetadata metadata = tramite.getDocumentos().stream()
                .filter(doc -> doc.getArchivoId().equals(archivoId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Metadatos del archivo no encontrados."));

        System.out.println("DEBUG descargarDocumento -> archivoId: " + archivoId);
        System.out.println("DEBUG descargarDocumento -> usuarioSolicitante: " + usuarioSolicitante);
        System.out.println("DEBUG descargarDocumento -> departamentoSolicitante: " + departamentoSolicitante);
        System.out.println("DEBUG descargarDocumento -> rolSolicitante: " + rolSolicitante);
        System.out.println("DEBUG descargarDocumento -> permisos del archivo: " + metadata.getPermisos());

        boolean tienePermiso = false;

        if ("ADMIN".equals(rolSolicitante)) {
            tienePermiso = true;
        }
        
        if (metadata.getPermisos() == null || metadata.getPermisos().isEmpty()) {
            System.out.println("DEBUG descargarDocumento -> Fallback de permisos vacíos activado.");
            tienePermiso = true;
        }
        
        // El cliente siempre solicita con su ID o email
        if (usuarioSolicitante != null && (usuarioSolicitante.equals(tramite.getClienteId()) || usuarioSolicitante.equals(tramite.getClienteEmail()))) {
            String permisoCliente = metadata.getPermisos().get("CLIENTE");
            System.out.println("DEBUG descargarDocumento -> permisoCliente: " + permisoCliente);
            if ("LECTURA".equals(permisoCliente) || "EDICION".equals(permisoCliente) || "AMBOS".equals(permisoCliente)) {
                tienePermiso = true;
            }
        }
        
        // El funcionario solicita con su departamento
        if (departamentoSolicitante != null) {
            String permisoDepto = metadata.getPermisos().get(departamentoSolicitante);
            System.out.println("DEBUG descargarDocumento -> permisoDepto para " + departamentoSolicitante + ": " + permisoDepto);
            if ("LECTURA".equals(permisoDepto) || "EDICION".equals(permisoDepto) || "AMBOS".equals(permisoDepto)) {
                tienePermiso = true;
            }
        }

        System.out.println("DEBUG descargarDocumento -> tienePermiso final: " + tienePermiso);

        if (!tienePermiso) {
            throw new RuntimeException("No tienes permisos para este documento.");
        }

        if (metadata.getRutaS3() == null || metadata.getRutaS3().trim().isEmpty()) {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(metadata.getRutaS3())
                .build();

        InputStream is = s3Client.getObject(getObjectRequest);

        auditoriaService.registrarEvento(
                tramite.getClienteId(),
                AuditoriaService.DOCUMENTO_LEIDO_DESCARGADO,
                usuarioSolicitante,
                departamentoSolicitante,
                tramite.getId(),
                archivoId,
                "Documento leído/descargado desde S3: " + metadata.getRutaS3()
        );

        return is;
    }

    private String determinarFormato(String filename) {
        if (filename == null) return "OTRO";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "WORD";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "EXCEL";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")) return "IMAGEN";
        return "OTRO";
    }
}
