package com.saver.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saver.config.AppProperties;
import org.junit.jupiter.api.Test;

class StorageConfigurationTest {
    private final StorageConfiguration configuration = new StorageConfiguration();

    @Test
    void createsS3CompatibleAdapterForDevelopment() {
        StorageService service = configuration.storageService(properties("s3-compatible", "development", "http://object-storage:8333"));

        assertThat(service).isInstanceOf(S3StorageService.class);
    }

    @Test
    void allowsRemoteS3CompatibleProviderInProduction() {
        AppProperties.Storage supabase = new AppProperties.Storage("s3-compatible", "production",
                "https://abc.supabase.co/storage/v1/s3", "receipts", "sa-east-1", "prod-key", "prod-secret", 1024);

        assertThat(configuration.storageService(withStorage(supabase))).isInstanceOf(S3StorageService.class);
    }

    @Test
    void rejectsPlainHttpOrLocalEndpointInProduction() {
        assertThatThrownBy(() -> configuration.storageService(withStorage(new AppProperties.Storage("s3-compatible", "production",
                "http://object-storage:8333", "b", "us-east-1", "prod-key", "prod-secret", 1024))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("remote HTTPS");
        assertThatThrownBy(() -> configuration.storageService(withStorage(new AppProperties.Storage("s3-compatible", "production",
                "https://localhost:8333", "b", "us-east-1", "prod-key", "prod-secret", 1024))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("remote HTTPS");
    }

    @Test
    void rejectsDevelopmentCredentialsInProduction() {
        assertThatThrownBy(() -> configuration.storageService(properties("s3-compatible", "production", "https://storage.example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development storage credentials");
    }

    @Test
    void gcsIsReservedForANativeAdapter() {
        assertThatThrownBy(() -> configuration.storageService(properties("gcs", "production", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GCS");
    }

    @Test
    void rejectsUnknownProviderAndIncompleteLocalCredentials() {
        assertThatThrownBy(() -> configuration.storageService(properties("unknown", "development", "http://object-storage:8333")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported");
        AppProperties.Storage invalid = new AppProperties.Storage("s3-compatible", "development", "", "bucket", "us-east-1", "", "", 10);
        assertThatThrownBy(() -> configuration.storageService(withStorage(invalid)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STORAGE_ENDPOINT");
    }

    private static AppProperties properties(String provider, String environment, String endpoint) {
        return withStorage(new AppProperties.Storage(provider, environment, endpoint, "saver-dev-media", "us-east-1", "saver-dev", "saver-dev-secret", 1024));
    }

    private static AppProperties withStorage(AppProperties.Storage storage) {
        return new AppProperties(
                new AppProperties.Jwt("test-secret-test-secret-test-secret-123", "saver-api", 15, 30),
                new AppProperties.Google(""), new AppProperties.Cors(java.util.List.of()),
                new AppProperties.Cookie(false, "Strict"), storage,
                new AppProperties.Defaults("UTC", "ARS", "es"));
    }
}
