package com.luxtrox.backend.exception;

/** Lanzar cuando un refresh token no existe, ya esta revocado, o expiro. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
