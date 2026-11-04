package com.saver.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/** Puerto de almacenamiento. El dominio persiste solo la clave estable que devuelve {@link #store}. */
public interface StorageService {
    StoredObject store(StorageUpload upload);
    InputStream open(String storageKey);
    void delete(String storageKey);
    Optional<StorageObjectMetadata> metadata(String storageKey);
    URI signedDownloadUrl(String storageKey, Duration validity);

    /** Prepara el almacenamiento (ej. crea el bucket en desarrollo). Por defecto no hace nada. */
    default void ensureReady() {}
}
