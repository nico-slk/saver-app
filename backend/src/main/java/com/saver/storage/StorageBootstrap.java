package com.saver.storage;

import com.saver.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * En desarrollo crea el bucket local al arrancar. Si el almacenamiento no está levantado
 * solo deja un aviso: la API arranca igual y los endpoints de archivos fallarán hasta que esté.
 */
@Component
class StorageBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StorageBootstrap.class);

    private final StorageService storage;
    private final boolean development;
    private final String bucket;

    StorageBootstrap(StorageService storage, AppProperties props) {
        this.storage = storage;
        this.development = "development".equalsIgnoreCase(props.storage().environment());
        this.bucket = props.storage().bucket();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!development) return;
        try {
            storage.ensureReady();
            log.info("Storage ready (bucket '{}')", bucket);
        } catch (RuntimeException e) {
            log.warn("Storage not reachable; start it with 'docker compose up -d'. Cause: {}", e.getMessage());
        }
    }
}
