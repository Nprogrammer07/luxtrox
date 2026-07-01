-- V23: Tabla de configuración del sistema -- los 4 valores del panel
-- de configuración del admin que antes eran constantes hardcodeadas
-- en PlanPricing.java y WithdrawalService.java. Ahora son mutables
-- desde el panel de admin (/admin/configuracion) sin necesitar un
-- redeploy del backend.
CREATE TABLE system_config (
    key         VARCHAR(100) PRIMARY KEY,
    value       VARCHAR(500) NOT NULL,
    description VARCHAR(500),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO system_config (key, value, description) VALUES
('driver_price',          '1099.00', 'Precio del paquete Driver (USD por seminario)'),
('max_driver_positions',  '30',      'Máximo de posiciones Driver permitidas por usuario'),
('cashback_rate_pct',     '300.00',  'Tasa de cashback total del plan Driver (%, 300 = 300%)'),
('min_withdrawal',        '50.00',   'Monto mínimo de retiro (USD)');