package com.luxtrox.backend.entity.enums;

public enum ReferralStatus {
    PENDING_PURCHASE,

    /**
     * @deprecated Ya no se usa -- el mecanismo de espera/reintento
     * cuando el referente todavia no calificaba fue eliminado (ver
     * docs/domain-model.md adenda de Fase 8). Se conserva el valor
     * solo por compatibilidad con filas historicas que puedan tenerlo.
     */
    @Deprecated
    QUALIFIED_AWAITING_REFERRER,

    /**
     * @deprecated Reemplazado por RESOLVED -- "BONUS_PAID" ya no es
     * preciso porque la evaluacion puede terminar en comision perdida
     * (forfeited), no solo pagada. Se conserva por compatibilidad con
     * filas historicas.
     */
    @Deprecated
    BONUS_PAID,

    /**
     * La compra del referido ya se evaluo, de una vez y para siempre
     * (sin reintentos). El resultado real -- pagada (total o
     * parcialmente) o perdida -- vive en cashback_transactions y
     * audit_logs, no en este enum.
     */
    RESOLVED
}
