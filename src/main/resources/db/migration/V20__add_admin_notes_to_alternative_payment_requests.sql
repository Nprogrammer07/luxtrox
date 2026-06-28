-- V20: admin_notes en alternative_payment_requests -- mismo patron
-- que withdrawal_requests.admin_notes. Rechazar un comprobante de
-- pago sin dejar constancia de POR QUE es un hueco real de auditoria
-- (encontrado al construir el flujo completo de pago alternativo,
-- pendiente desde la Fase 7).
ALTER TABLE alternative_payment_requests ADD COLUMN admin_notes VARCHAR(500);