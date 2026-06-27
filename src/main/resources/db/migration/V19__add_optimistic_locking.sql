-- V19: bloqueo optimista en users, investment_positions, y
-- monthly_performances -- sin esto, dos modificaciones concurrentes
-- al mismo registro pueden causar dos problemas distintos:
--   1. Update perdido (users, investment_positions): dos
--      transacciones leen el mismo valor antes de que cualquiera
--      guarde, ambas pasan su validacion, y el efecto neto termina
--      siendo solo UNA de las dos actualizaciones en vez de ambas.
--   2. Doble pago (monthly_performances): distribute() depende de
--      "applied_at IS NULL" para no reaplicar un reparto -- si se
--      llama dos veces casi al mismo tiempo (ej. el admin hace doble
--      clic), ambas llamadas pueden leer applied_at=NULL ANTES de que
--      cualquiera lo marque, y ambas reparten el rendimiento completo
--      a TODAS las posiciones -- dinero pagado dos veces, no perdido.
-- Encontrado al escribir el test de estres de la Fase 8. @Version
-- hace que la segunda transaccion en terminar falle con un error
-- claro (409, ver GlobalExceptionHandler) en vez de pisar
-- silenciosamente a la primera o duplicar el pago.
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE investment_positions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE monthly_performances ADD COLUMN version BIGINT NOT NULL DEFAULT 0;