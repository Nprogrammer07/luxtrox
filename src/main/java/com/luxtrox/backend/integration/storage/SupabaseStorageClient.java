package com.luxtrox.backend.integration.storage;

import org.springframework.context.annotation.Lazy;
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
 *
 * @Lazy a proposito: si app.storage.endpoint queda vacio (Fase 9 lo
 * permite -- el health indicator lo reporta DOWN sin tumbar el resto
 * de la app), URI.create("") + S3Client.builder().endpointOverride(...)
 * lanza NullPointerException ("URI scheme... must not be null")
 * DENTRO del constructor. Sin @Lazy, Spring construye este bean
 * EAGER al arrancar (porque PurchaseService/InvoiceService lo
 * inyectan), y esa excepcion tumba TODA la aplicacion -- justo lo que
 * el diseno de Fase 9 queria evitar (encontrado al correr la imagen
 * de Docker con Storage sin configurar). Con @Lazy, Spring inyecta un
 * proxy y el constructor real solo corre la PRIMERA VEZ que algo
 * llama uploadFile/downloadFile -- si eso falla, ya esta dentro del
 * try/catch de generateInvoiceAndNotify en PurchaseService, que no
 * tumba la confirmacion de la compra.
 */
@Component
@Lazy
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
