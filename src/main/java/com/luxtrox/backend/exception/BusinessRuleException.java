package com.luxtrox.backend.exception;

/**
 * Lanzar cuando se viola una regla de negocio (no un error tecnico):
 * tope de 30 paquetes, retiro menor a 50 USD, etc. Ver
 * docs/domain-model.md para las reglas exactas.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
