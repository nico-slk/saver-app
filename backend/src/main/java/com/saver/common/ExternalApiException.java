package com.saver.common;

/** Una API externa (inflación, cotizaciones) no respondió o respondió algo inesperado. */
public class ExternalApiException extends RuntimeException {
    public ExternalApiException(String message) { super(message); }
    public ExternalApiException(String message, Throwable cause) { super(message, cause); }
}
