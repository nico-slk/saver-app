package com.saver.common;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio. {@code code} es una clave estable (ej. "auth.invalid_credentials")
 * que el frontend traduce con i18n; el backend no devuelve textos traducidos.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
