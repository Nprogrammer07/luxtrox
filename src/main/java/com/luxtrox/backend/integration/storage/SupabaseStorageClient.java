package com.luxtrox.backend.integration.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/**
 * Cliente S3 apuntando a Supabase Storage (S3-compatible, no AWS
 * real -- ver docs/domain-model.md adenda de Fase 7). El "storage
 * key" que devuelve uploadFile es lo que se guarda en
 * Invoice.pdfStorageKey / AlternativePaymentRequest.paymentProofStorageKey.
 *
 * forcePathStyle=true es OBLIGATORIO para Supabase -- sin esto, el SDK
 * intenta resolver un estilo de URL "virtual-hosted" (bucket.endpoint)
 * que Supabase no soporta.
 */
@Component
public class SupabaseStorageClient {

    private final S3Client s3Client;
    private final String bucket;

    public SupabaseStorageClient(StorageProperties properties) {
        this.bucket = properties.getBucket();
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey())))
                .forcePathStyle(true)
                .build();
    }

    /** Sube el archivo y devuelve la key con la que se guardo (no una URL). */
    public String uploadFile(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content)
        );
        return key;
    }

    public byte[] downloadFile(String key) {
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build()
        ).asByteArray();
    }
}
