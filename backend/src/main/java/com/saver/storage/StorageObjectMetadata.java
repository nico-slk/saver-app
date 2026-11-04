package com.saver.storage;

import java.time.Instant;

public record StorageObjectMetadata(String storageKey, long contentLength, String contentType, Instant lastModified) {}
