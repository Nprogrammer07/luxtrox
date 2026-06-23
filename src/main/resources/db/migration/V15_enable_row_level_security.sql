-- V15: Activa Row Level Security en todas las tablas.
--
-- Por que: Supabase expone automaticamente cada tabla del esquema
-- "public" via su API REST autogenerada (PostgREST), accesible con la
-- "anon key". Esta plataforma nunca debe atenderse via esa API -- todo
-- acceso pasa por el backend de Spring Boot, que se conecta con el rol
-- "postgres" (tiene BYPASSRLS por defecto, no se ve afectado por esto).
--
-- No se definen policies a proposito: con RLS activo y CERO policies,
-- cualquier rol SIN bypassrls (como "anon" o "authenticated", los que
-- usa PostgREST) no puede ver ni modificar ninguna fila. Es el
-- equivalente a "bloquear por completo el acceso publico directo".
ALTER TABLE roles                       ENABLE ROW LEVEL SECURITY;
ALTER TABLE users                       ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchases                   ENABLE ROW LEVEL SECURITY;
ALTER TABLE investment_positions        ENABLE ROW LEVEL SECURITY;
ALTER TABLE monthly_performances        ENABLE ROW LEVEL SECURITY;
ALTER TABLE referrals                   ENABLE ROW LEVEL SECURITY;
ALTER TABLE cashback_transactions       ENABLE ROW LEVEL SECURITY;
ALTER TABLE withdrawal_requests         ENABLE ROW LEVEL SECURITY;
ALTER TABLE crypto_withdrawal_details   ENABLE ROW LEVEL SECURITY;
ALTER TABLE bank_withdrawal_details     ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoices                    ENABLE ROW LEVEL SECURITY;
ALTER TABLE alternative_payment_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications               ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens              ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs                  ENABLE ROW LEVEL SECURITY;