# Conexión a Supabase + cómo correr las migraciones

## 1. Obtener el connection string

En el dashboard de tu proyecto Supabase: **Project Settings → Database → Connection string**.

Usa el modo **Session pooling** (puerto `5432`) para Flyway, no el modo Transaction pooling
(puerto `6543`) — Flyway necesita una sesión persistente para sus operaciones de bloqueo
durante la migración.

El formato es:
```
jdbc:postgresql://db.<tu-proyecto-ref>.supabase.co:5432/postgres
```

Usuario: `postgres`
Password: la que configuraste al crear el proyecto (o la puedes resetear en el mismo dashboard).

## 2. Correr las migraciones

**Nunca** pongas la contraseña dentro de `pom.xml` ni de ningún archivo que se vaya a commitear.

Desde la raíz del proyecto (`luxtrox-backend/`):

```bash
mvn flyway:migrate \
  -Dflyway.url="jdbc:postgresql://db.<tu-proyecto-ref>.supabase.co:5432/postgres" \
  -Dflyway.user="postgres" \
  -Dflyway.password="<tu-password>"
```

Alternativa más cómoda — crea un archivo `flyway.conf` en la raíz del proyecto (ya está en
`.gitignore`, nunca se sube):

```properties
flyway.url=jdbc:postgresql://db.<tu-proyecto-ref>.supabase.co:5432/postgres
flyway.user=postgres
flyway.password=<tu-password>
```

Y luego simplemente:
```bash
mvn flyway:migrate
```

## 3. Verificar que corrió bien

```bash
mvn flyway:info
```

Debe mostrar las 14 migraciones (`V1` a `V14`) con estado `Success`.

## 4. Si algo falla a mitad de camino

Flyway crea una tabla `flyway_schema_history` que registra qué migraciones ya corrieron.
Si una migración falla, Flyway la marca como fallida y **no** sigue con las siguientes hasta
que se resuelva (revisar el error, corregir el `.sql`, y correr `mvn flyway:repair` antes de
reintentar `mvn flyway:migrate`).

## 5. Qué se crea

15 tablas (ver `docs/domain-model.md` para el detalle de cada una):

```
roles, users, purchases, investment_positions, monthly_performances,
referrals, cashback_transactions, withdrawal_requests,
crypto_withdrawal_details, bank_withdrawal_details, invoices,
alternative_payment_requests, notifications, refresh_tokens, audit_logs
```

Más el seed inicial de `roles` (`ADMIN`, `USER`).
