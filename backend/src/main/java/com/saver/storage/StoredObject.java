package com.saver.storage;

public record StoredObject(String storageKey, long contentLength, String contentType) {}
