package backendworkflow.backend.services;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.PublicAccessType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class AzureBlobStorageService {

    private final BlobContainerClient containerClient;

    public AzureBlobStorageService(
            @Value("${AZURE_STORAGE_CONNECTION_STRING}") String connectionString,
            @Value("${AZURE_STORAGE_CONTAINER_NAME}") String containerName) {
        
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        
        this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!this.containerClient.exists()) {
            this.containerClient.create();
            this.containerClient.setAccessPolicy(PublicAccessType.BLOB, null);
        }
    }

    public String uploadFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "file";
        }
        
        // Use UUID to avoid name collisions
        String blobName = UUID.randomUUID().toString() + "-" + originalFilename;
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        
        blobClient.upload(file.getInputStream(), file.getSize(), true);
        return blobClient.getBlobUrl();
    }
}
