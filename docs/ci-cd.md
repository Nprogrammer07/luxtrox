# CI/CD (Fase 11)

Vive en `.github/workflows/ci.yml`. Dos jobs:

## 1. `test`

Corre en **cada push y cada pull request** contra `main`. Ejecuta `mvn clean test`,
exactamente el mismo comando que corres en tu máquina -- esto incluye:

- Tests unitarios con Mockito.
- Tests de integración con Testcontainers (Postgres real, vía Docker -- los runners
  de GitHub ya traen Docker instalado, no hace falta configurar nada extra).
- Tests de API con RestAssured.
- El gate de cobertura de JaCoCo (90% sobre los paquetes de lógica real -- ver
  `pom.xml`, Fase 8). **Si la cobertura cae por debajo del umbral, el pipeline falla
  aquí.**

No necesita ningún secret configurado en GitHub -- todas las credenciales que los
tests usan (JWT, NOWPayments, Resend, Storage) son valores falsos fijos en el código
de los tests mismos (`AbstractIntegrationTest`, `AbstractApiTest`), no vienen del
entorno real.

El reporte de cobertura (`target/site/jacoco/`) queda disponible como artefacto
descargable en cada corrida, durante 14 días -- lo encuentras en la pestaña
**Actions** de GitHub, abriendo la corrida correspondiente, sección "Artifacts".

## 2. `docker`

Solo corre en **push a `main`** (nunca en pull requests) y solo si `test` pasó.
Construye la misma imagen del `Dockerfile` de la Fase 10, sin cambios, y la publica en
**GitHub Container Registry** con dos tags:

- `latest` -- siempre la última de `main`.
- El SHA corto del commit -- para poder fijar un despliegue a una versión exacta.

La encuentras en `https://github.com/<tu-usuario>/<tu-repo>/pkgs/container/<tu-repo>`
(la pestaña "Packages" del repo).

No requiere ningún secret manual -- usa el `GITHUB_TOKEN` que GitHub Actions provee
automáticamente, con permiso de escritura sobre paquetes (`packages: write`, ya
configurado en el workflow).

## Qué falta (Fase 12)

Decidir dónde vive la app en producción y conectarla a esta imagen (o hacer que la
plataforma de despliegue construya su propia imagen directo del mismo `Dockerfile` --
ambas opciones funcionan sin tocar este pipeline).