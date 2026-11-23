package com.saver.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Archivos en una carpeta local ({@code provider=filesystem}). Solo para desarrollo sin Docker:
 * en producción se rechaza. La clave es un UUID (sin nombre original ni rutas del usuario).
 */
public class FileSystemStorageService implements StorageService {

    private final Path root;
    private final long maxFileSizeBytes;

    public FileSystemStorageService(Path root, long maxFileSizeBytes) {
        this.root = root.toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public StoredObject store(StorageUpload upload) {
        if (upload.contentLength() > maxFileSizeBytes) throw new StorageException("File too large");
        String key = "objects/" + UUID.randomUUID();
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            long written = Files.copy(upload.content(), target, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(meta(target), upload.contentType());
            return new StoredObject(key, written, upload.contentType());
        } catch (IOException e) {
            throw new StorageException("Cannot store file", e);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        try {
            return Files.newInputStream(resolve(storageKey));
        } catch (IOException e) {
            throw new StorageException("Cannot open file", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path p = resolve(storageKey);
            Files.deleteIfExists(p);
            Files.deleteIfExists(meta(p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<StorageObjectMetadata> metadata(String storageKey) {
        Path p = resolve(storageKey);
        if (!Files.exists(p)) return Optional.empty();
        try {
            String type = Files.exists(meta(p)) ? Files.readString(meta(p)) : "application/octet-stream";
            return Optional.of(new StorageObjectMetadata(storageKey, Files.size(p), type, Files.getLastModifiedTime(p).toInstant()));
        } catch (IOException e) {
            throw new StorageException("Cannot read metadata", e);
        }
    }

    @Override
    public URI signedDownloadUrl(String storageKey, Duration validity) {
        throw new UnsupportedOperationException("filesystem storage has no signed URLs; stream through the API");
    }

    @Override
    public void ensureReady() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new StorageException("Cannot create storage folder " + root, e);
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new StorageException("Invalid storage key");
        return p;
    }

    private static Path meta(Path p) {
        return p.resolveSibling(p.getFileName() + ".type");
    }
}
