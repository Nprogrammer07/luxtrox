package com.luxtrox.backend.exception;

/** Lanzar cuando se busca una entidad por id/clave y no existe. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
