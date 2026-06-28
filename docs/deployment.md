# Despliegue en Railway (Fase 12)

## 1. Crear el proyecto

1. Entra a [railway.com](https://railway.com), crea una cuenta si no tienes (puedes
   entrar directo con tu cuenta de GitHub).
2. **New Project** → **Deploy from GitHub repo** → selecciona tu repo
   (`luxtrox-backend`).
3. Railway detecta el `Dockerfile` automáticamente (siempre lo prioriza si lo
   encuentra) y el `railway.toml` que ya está en la raíz del repo -- no tienes que
   tocar la configuración de build manualmente.

## 2. Variables de entorno

En el servicio recién creado → pestaña **Variables**, agrega cada una de estas. Son
las mismas que ya conoces de `.env.example` (Fase 10), pero ahora con valores
**reales de producción**, no los de prueba:

```
DB_URL=jdbc:postgresql://<tu-host-de-supabase>:5432/postgres
DB_USERNAME=postgres.<tu-ref>
DB_PASSWORD=<tu-password-real-de-supabase>

JWT_SECRET=<genera uno NUEVO, distinto al de tu .env local>

NOWPAYMENTS_API_KEY=<tu api key real>
NOWPAYMENTS_IPN_SECRET=<tu ipn secret real>
NOWPAYMENTS_IPN_CALLBACK_URL=<ver paso 4 -- necesitas el dominio primero>

RESEND_API_KEY=<tu api key real>

SUPABASE_STORAGE_ENDPOINT=<tu endpoint real>
SUPABASE_STORAGE_REGION=<tu region real>
SUPABASE_STORAGE_ACCESS_KEY_ID=<tu access key real>
SUPABASE_STORAGE_SECRET_ACCESS_KEY=<tu secret key real>

CORS_ALLOWED_ORIGINS=<la URL real de tu frontend, cuando la tengas>
```

**Importante:**
- **NO** agregues una variable `PORT` -- Railway la inyecta sola, y
  `application.yml` ya la lee automáticamente (ver el cambio de esta fase).
- **NO** reuses el mismo `JWT_SECRET` de tu `.env` local -- genera uno nuevo y
  manténlo solo aquí.
- Si todavía no tienes credenciales reales de NOWPayments/Resend/Storage para
  producción, puedes dejarlas vacías por ahora -- la app arranca igual (Fase 9), y
  `/actuator/health` (con un token de ADMIN) te va a mostrar exactamente cuáles
  faltan, sin que eso tumbe el resto del backend.

## 3. Primer deploy

Railway empieza a construir automáticamente en cuanto guardas las variables (o en
cuanto conectaste el repo, lo que pase primero). Puedes ver el progreso en vivo en la
pestaña **Deployments** -- es el mismo build que ya viste correr en Docker localmente
(Fase 10), solo que ahora corre en la infraestructura de Railway.

Cuando termine, Railway espera a que `/actuator/health` responda 200 antes de
enrutarle tráfico real a esa versión (`healthcheckPath` en `railway.toml`) -- así
nunca te quedas con una versión rota recibiendo tráfico.

## 4. Dominio público

En la pestaña **Settings** del servicio → **Networking** → **Generate Domain**.
Railway te da algo como `tu-proyecto.up.railway.app`.

Con ese dominio en mano:
1. Pruébalo: `https://tu-proyecto.up.railway.app/actuator/health` debe responder.
2. Vuelve a la variable `NOWPAYMENTS_IPN_CALLBACK_URL` y pon
   `https://tu-proyecto.up.railway.app/webhooks/nowpayments/ipn` -- NOWPayments
   necesita esta URL real para poder notificarte cuando un pago se confirma.
3. Cuando tengas el frontend desplegado en alguna parte, actualiza
   `CORS_ALLOWED_ORIGINS` con esa URL real (no dejes `localhost:3000` en
   producción).

## 5. Despliegue automático en cada push

Por defecto, Railway redespliega automáticamente en cada push a `main` -- el mismo
disparador que ya activa el pipeline de GitHub Actions (Fase 11).

Recomendado: en **Settings** → **Source** del servicio, activa **"Wait for CI"**. Así
Railway espera a que el job `test` de GitHub Actions pase (incluyendo el gate de
cobertura del 90%) antes de intentar desplegar -- nunca vas a desplegar una versión
que ni siquiera pasó sus propios tests.

## 6. Crear tu primer usuario ADMIN

El registro público (`POST /auth/register`) siempre asigna rol `USER` -- es lo
correcto, nadie debería poder auto-asignarse `ADMIN` por la API. Pero entonces,
¿cómo creas el primer admin en producción, donde no tienes acceso directo y cómodo a
la base de datos?

1. Regístrate normal, por la API pública, con el correo que va a ser tu cuenta admin:
   ```
   POST https://tu-proyecto.up.railway.app/auth/register
   {"fullName": "Tu Nombre", "email": "tu-correo@ejemplo.com", "phone": "+1", "password": "..."}
   ```
2. En Railway → Variables, agrega:
   ```
   BOOTSTRAP_ADMIN_EMAIL=tu-correo@ejemplo.com
   ```
3. Eso dispara un redeploy automático. Al arrancar, la app encuentra ese usuario y lo
   promueve a `ADMIN` -- vas a ver una línea de log como
   `Usuario 'tu-correo@ejemplo.com' promovido a ADMIN...` en los Deploy Logs.
4. **Quita esa variable de entorno** ahora que la promoción se confirmó. Si la dejas
   puesta y más adelante le quitas el rol admin a esa cuenta a propósito, el
   siguiente reinicio la volvería a promover sin que lo pidieras.
5. Haz login normal (`POST /auth/login`) con ese mismo correo -- el token que recibas
   ya trae el rol `ADMIN`, listo para usar en `/admin/**`.



Railway construye su propia imagen directo del `Dockerfile` -- no necesita la imagen
que el pipeline de CI publica en GHCR para funcionar. Esa imagen sigue siendo útil por
separado: para hacer rollback a una versión exacta, o para correr en tu máquina la
versión EXACTA que está en producción si necesitas reproducir un bug.

## Cuando llegue el momento de migrar

Esta guía es deliberadamente la ruta más simple para arrancar -- el `Dockerfile` y
`railway.toml` no son específicos de Railway de ninguna forma que te ate: cualquier
plataforma que sepa construir desde un Dockerfile (Render, Fly.io, un VPS con Docker
Compose usando el mismo `docker-compose.yml` de la Fase 10, etc.) puede tomar este
mismo proyecto sin cambios.