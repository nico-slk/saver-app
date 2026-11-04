package com.saver.storage;

/** Error técnico de almacenamiento; no incluye rutas ni contenido sensible. */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
