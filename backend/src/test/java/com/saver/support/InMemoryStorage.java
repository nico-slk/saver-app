package com.saver.support;

import com.saver.storage.StorageObjectMetadata;
import com.saver.storage.StorageService;
import com.saver.storage.StorageUpload;
import com.saver.storage.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Storage en memoria para tests (no hace falta SeaweedFS). */
@TestConfiguration
public class InMemoryStorage {

    public static final Map<String, byte[]> FILES = new ConcurrentHashMap<>();

    @Bean
    @Primary
    StorageService inMemoryStorageService() {
        return new StorageService() {
            @Override public StoredObject store(StorageUpload u) {
                try {
                    String key = "test/" + UUID.randomUUID();
                    FILES.put(key, u.content().readAllBytes());
                    return new StoredObject(key, u.contentLength(), u.contentType());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            @Override public InputStream open(String key) { return new ByteArrayInputStream(FILES.get(key)); }
            @Override public void delete(String key) { FILES.remove(key); }
            @Override public Optional<StorageObjectMetadata> metadata(String key) {
                return Optional.ofNullable(FILES.get(key)).map(b -> new StorageObjectMetadata(key, b.length, "application/octet-stream", Instant.now()));
            }
            @Override public URI signedDownloadUrl(String key, Duration validity) { return URI.create("memory:" + key); }
        };
    }
}
