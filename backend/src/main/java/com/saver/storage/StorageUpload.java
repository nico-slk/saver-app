package com.saver.storage;

import java.io.InputStream;

/** El nombre original no forma parte de la clave ni de una ruta del proveedor. */
public record StorageUpload(InputStream content, long contentLength, String contentType) {}
