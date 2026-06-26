-- V18: Reglas de elegibilidad de comision de referido (correccion de
-- fondo -- reemplaza la idea original de "cualquier compra confirmada
-- califica" y elimina el mecanismo de espera/reintento. Ver
-- docs/domain-model.md adenda de Fase 8 para el detalle completo).
--
-- Ya no existe "esperar a que el referente califique despues" -- la
-- evaluacion es UNICA, en el momento exacto en que se confirma la
-- compra del referido. QUALIFIED_AWAITING_REFERRER y BONUS_PAID
-- quedan en el CHECK solo por compatibilidad con filas historicas que
-- ya pudieran tener esos valores -- el codigo nuevo nunca los vuelve
-- a escribir, usa RESOLVED en su lugar.
ALTER TABLE referrals DROP CONSTRAINT referrals_status_check;
ALTER TABLE referrals ADD CONSTRAINT referrals_status_check
    CHECK (status IN ('PENDING_PURCHASE', 'QUALIFIED_AWAITING_REFERRER', 'BONUS_PAID', 'RESOLVED'));
