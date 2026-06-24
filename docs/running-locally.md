# Correr el backend localmente

## 1. Credenciales de base de datos

`application.yml` (el que SÍ se commitea) espera las credenciales como variables de
entorno (`${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`) — nunca hardcodeadas ahí.

Para evitar pelear con la sintaxis de variables de entorno entre PowerShell/bash (ya
tuvimos ese problema con Flyway), la forma más simple es crear un archivo de **perfil
local** que sí tiene los valores reales, pero que **nunca se commitea** (ya está en
`.gitignore`):

Crea `src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://aws-1-sa-east-1.pooler.supabase.com:5432/postgres
    username: postgres.iutobszqtmrmzyxgyhnw
    password: tu-password-real

app:
  jwt:
    secret: pon-aqui-cualquier-string-aleatorio-de-al-menos-32-caracteres
```

Usa los **mismos 3 valores** que ya validaste funcionando en tu `flyway.conf` — el
host del Session pooler, el usuario `postgres.<tu-ref>`, y tu password — solo que aquí
van en 3 propiedades separadas en vez de una sola URL con `?user=...&password=...`.

**Sobre `app.jwt.secret`** (agregado en la Fase 5): es la clave con la que se firman
los access tokens. Para desarrollo local cualquier string aleatorio de 32+ caracteres
sirve — pero **en producción debe ser distinto, secreto, y nunca el mismo que uses
aquí**. Lo configuras en Railway (o donde despliegues) como variable de entorno
`JWT_SECRET`, nunca en un archivo del repo.

## 2. Correr la aplicación

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Esto arranca el backend en `http://localhost:8080`, activando el perfil `local` que
sobreescribe las credenciales de `application.yml` con las de `application-local.yml`.

## 3. Qué debería pasar

- La consola debe mostrar a Hibernate validando cada una de las 15 entidades contra
  las tablas reales (`hibernate.ddl-auto: validate`). **Si algo no calza, la app NO
  arranca** y te muestra exactamente qué entidad/columna falló — eso es intencional,
  es nuestra red de seguridad para detectar errores de mapeo antes de que lleguen a
  producción.
- Si todo calza: la consola termina con algo como `Started LuxtroxBackendApplication
  in X seconds`.

## 4. Verificar que quedó arriba

Con la app corriendo, abre en el navegador:

- **`http://localhost:8080/actuator/health`** → debe responder `{"status":"UP"}`
- **`http://localhost:8080/docs`** → Swagger UI, debe mostrar la API. Desde la Fase 5
  ya deberías ver el grupo **Auth** con `/auth/register`, `/auth/login`, `/auth/refresh`
  y `/auth/logout` — pruébalos directo ahí, sin necesidad de Postman.

## 5. Probar el registro/login manualmente (opcional)

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Carlos Mendoza","email":"carlos@example.com","phone":"+57300","password":"password123"}'
```

Debe responder con `accessToken`, `refreshToken` y los datos del usuario (incluyendo
su `referralCode` generado). Guarda el `accessToken` y pruébalo en cualquier endpoint
protegido con el header `Authorization: Bearer <token>`.

Si el paso 3 falla, pégame el error completo de la consola — probablemente sea un
desajuste entre alguna entidad y la tabla real, y lo corregimos igual que hicimos con
la conexión de Supabase.
