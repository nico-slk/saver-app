package com.saver.storage;

import com.saver.config.AppProperties;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Elige el adaptador de almacenamiento según {@code app.storage.provider}.
 *
 * <ul>
 *   <li>{@code s3-compatible}: cualquier servicio con API S3 y endpoint propio. En local es
 *       SeaweedFS (Docker, funciona sin internet); en producción puede ser Supabase Storage,
 *       Cloudflare R2 o GCS en modo interoperable (claves HMAC).</li>
 *   <li>{@code s3}: AWS S3 nativo, credenciales de la identidad del entorno.</li>
 *   <li>{@code gcs}: reservado para un adaptador nativo de Google Cloud Storage.</li>
 * </ul>
 *
 * Para sumar un proveedor nuevo: implementar {@link StorageService} y agregarlo al switch.
 * El resto del código depende solo de la interfaz.
 */
@Configuration
public class StorageConfiguration {

    /** Credenciales de ejemplo del .env.example: nunca válidas en producción. */
    private static final Set<String> DEV_CREDENTIALS = Set.of("saver-dev", "saver-dev-secret");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "0.0.0.0");

    @Bean
    StorageService storageService(AppProperties properties) {
        AppProperties.Storage config = properties.storage();
        boolean production = "production".equalsIgnoreCase(config.environment());
        String provider = config.provider().trim().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "s3-compatible" -> s3Compatible(config, production);
            case "s3" -> awsS3(config);
            case "gcs" -> throw new IllegalStateException(
                    "Native GCS adapter is not implemented yet; use provider=s3-compatible with GCS HMAC keys "
                            + "or implement StorageService for GCS");
            default -> throw new IllegalStateException("Unsupported storage provider: " + config.provider());
        };
    }

    private static StorageService s3Compatible(AppProperties.Storage config, boolean production) {
        URI endpoint = endpoint(config.endpoint());
        String accessKey = required(config.accessKey(), "STORAGE_ACCESS_KEY");
        String secretKey = required(config.secretKey(), "STORAGE_SECRET_KEY");
        if (production) {
            if (!"https".equals(endpoint.getScheme()) || LOCAL_HOSTS.contains(endpoint.getHost())) {
                throw new IllegalStateException("Production STORAGE_ENDPOINT must be a remote HTTPS URL");
            }
            if (DEV_CREDENTIALS.contains(accessKey) || DEV_CREDENTIALS.contains(secretKey)) {
                throw new IllegalStateException("Production cannot use the development storage credentials");
            }
        }
        Region region = Region.of(config.region());
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        // Path-style (endpoint/bucket/key): lo requieren SeaweedFS, Supabase Storage y R2.
        S3Client client = S3Client.builder()
                .region(region)
                .endpointOverride(endpoint)
                .forcePathStyle(true)
                .credentialsProvider(credentials)
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .region(region)
                .endpointOverride(endpoint)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(credentials)
                .build();
        return new S3StorageService(client, presigner, config.bucket(), config.maxFileSizeBytes());
    }

    private static StorageService awsS3(AppProperties.Storage config) {
        if (config.endpoint() != null && !config.endpoint().isBlank()) {
            throw new IllegalStateException("STORAGE_ENDPOINT must be empty for provider=s3");
        }
        Region region = Region.of(config.region());
        S3ClientBuilder client = S3Client.builder().region(region).credentialsProvider(DefaultCredentialsProvider.create());
        S3Presigner presigner = S3Presigner.builder().region(region)
                .credentialsProvider(DefaultCredentialsProvider.create()).build();
        return new S3StorageService(client.build(), presigner, config.bucket(), config.maxFileSizeBytes());
    }

    private static URI endpoint(String endpoint) {
        String value = required(endpoint, "STORAGE_ENDPOINT");
        URI uri = URI.create(value);
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            throw new IllegalStateException("STORAGE_ENDPOINT must be an HTTP(S) URL");
        }
        return uri;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
