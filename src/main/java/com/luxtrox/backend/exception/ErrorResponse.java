package com.luxtrox.backend.exception;

import java.time.OffsetDateTime;

/** Forma estandar de cada respuesta de error de la API. */
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, path);
    }
}
