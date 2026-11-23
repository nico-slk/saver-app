package com.saver.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Error de negocio. {@code code} es una clave estable (ej. "auth.invalid_credentials")
 * que el frontend traduce con i18n; el backend no devuelve textos traducidos.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String code) {
        this(status, code, Map.of());
    }

    private ApiException(HttpStatus status, String code, Map<String, String> fields) {
        super(code);
        this.status = status;
        this.code = code;
        this.fields = fields;
    }

    /** Error 400 de un campo del formulario: {@code code} también va en {@code fields[field]}. */
    public static ApiException field(String field, String code) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, Map.of(field, code));
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getFields() { return fields; }
}
