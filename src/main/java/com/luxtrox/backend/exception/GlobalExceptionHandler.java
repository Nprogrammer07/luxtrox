package com.luxtrox.backend.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Se dispara cuando dos operaciones concurrentes intentan
     * modificar el mismo registro (ej. dos retiros simultaneos del
     * mismo usuario, ver @Version en User) -- 409 Conflict es el
     * codigo correcto para "esto cambio mientras procesabas tu
     * solicitud, intenta de nuevo", no un 500 generico. Puede surgir
     * recien al hacer COMMIT, despues de que el metodo de servicio ya
     * retorno -- por eso se atrapa aqui, a nivel global, y no dentro
     * del propio servicio.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                                WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of(409, "CONCURRENT_MODIFICATION",
                        "Tu saldo o solicitud cambio mientras se procesaba esta accion -- intenta de nuevo",
                        path(request))
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.of(404, "NOT_FOUND", ex.getMessage(), path(request))
        );
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponse.of(422, "BUSINESS_RULE_VIOLATION", ex.getMessage(), path(request))
        );
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex,
                                                                     WebRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse.of(401, "INVALID_REFRESH_TOKEN", ex.getMessage(), path(request))
        );
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthentication(Exception ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse.of(401, "INVALID_CREDENTIALS", "Email o contrasena incorrectos", path(request))
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                  WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", java.time.OffsetDateTime.now());
        body.put("status", 400);
        body.put("error", "VALIDATION_ERROR");
        body.put("path", path(request));

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        body.put("fields", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of(500, "INTERNAL_ERROR", "Ocurrio un error inesperado", path(request))
        );
    }

    private String path(WebRequest request) {
        String desc = request.getDescription(false);
        return desc.startsWith("uri=") ? desc.substring(4) : desc;
    }
}

