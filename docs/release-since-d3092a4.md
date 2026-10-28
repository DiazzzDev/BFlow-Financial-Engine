# Resumen de release desde `d3092a4`

**Rango:** desde `d3092a4f36d8d1e2c6329d25447c6def08b785fd` (28 de septiembre de 2026) hasta `f39f068` (22 de octubre de 2026), más cambios locales observados el 28 de octubre de 2026. Los cambios locales se identifican aparte porque todavía no forman parte de `HEAD`.

## Resumen

Desde el commit base, BFlow amplió flujos de wallets, recurrencias, recibos y suscripciones; actualizó dependencias por seguridad; migró el aprovisionamiento de infraestructura de scripts Bash a Terraform; y añadió observabilidad OpenTelemetry optativa con mejores logs de acceso y trazas. El estado local añade endurecimiento en la eliminación de cuentas y la ejecución de recurrencias, junto con nuevos gates de CI y cobertura.

## Cambios integrados después del commit base

### Finanzas, wallets y transacciones recurrentes

- Se incorporó el flujo de wallet predeterminado y cambios para resolver el `walletId` de registros de recibos.
- Se añadieron validaciones de recurrencias y soporte de frecuencia anual, incluida la migración `V37__allow_yearly_recurring_frequency.sql`.
- Se añadió la migración `V36__default_wallet_and_wallet_invitation_notifications.sql` para wallets y notificaciones de invitación.
- Se ajustó la gestión de la zona horaria de El Salvador.

### Recibos y OCR

- Se incorporó la llamada a Textract en un endpoint y se ajustaron las restricciones de categorías permitidas para la carga de recibos.
- Se mantuvo la integración de OCR/mensajería en la definición Terraform de infraestructura.

### Seguridad y dependencias

- Se actualizaron Apache Maven, AWS SDK BOM, PostgreSQL, Jackson y módulos Netty en commits de dependencias.
- Se añadieron overrides de Jackson para resolver CVEs reportadas por Trivy y se aplicaron hotfixes a la imagen Maven/Temurin.
- Se actualizaron excepciones de Trivy para hallazgos de CVE de la imagen/agent de OpenTelemetry.

### Infraestructura y despliegue

- Se migró el aprovisionamiento de AWS desde secuencias de scripts Bash a Terraform bajo `infra/terraform/`, incluyendo red, compute/storage, IAM, mensajería, OIDC de GitHub, observabilidad, DNS sync y outputs.
- Se añadieron `terraform.tfvars.example`, lockfile de proveedores, ADR de migración y nueva guía de infraestructura. Los scripts Bash de bootstrap, destroy, S3 y Textract que fueron reemplazados se eliminaron.
- Se simplificaron `infra/deploy.sh` y `infra/destroy.sh` como wrappers de Terraform.
- Se añadió `infra-ci.yml` para ejecutar formato y validación Terraform sin credenciales AWS cuando los PR modifican `infra/**`; también valida plantillas ECS y mantiene guard rails de valores económicos.
- Se adaptaron el workflow de despliegue, Dockerfile, plantilla de task definition y `.devcontainer` a la infraestructura actual.
- Se añadieron auditorías de coste/observabilidad y un benchmark local de OpenTelemetry.

### Observabilidad

- Se incorporó el agente ADOT Java como opción mediante `OTEL_ENABLED`, junto con configuración de acceso JSON y `trace_id`/`span_id` en logs.
- Se configuraron atributos de recurso y exportación de trazas en ECS; el despliegue controla la activación y el ratio de muestreo mediante variables.
- Se redactan parámetros sensibles de URL en spans de OpenTelemetry.
- Se añadió `docs/otel-rollout.md` con guía de despliegue, auditoría y pruebas locales.
- Se añadieron filtros de sanitización para valores como tokens, credenciales y datos personales en query strings.

### Validaciones y errores

- Se corrigió un `NullPointerException` al validar el servicio legal cuando faltaban datos requeridos.

## Cambios locales aún no integrados en `HEAD`

- Se sustituyó `.github/workflows/ci.yml` por una estructura con `verify`, `mcp-apps-qa` y `security`. `verify` conserva PostgreSQL y configuración de entorno, ejecuta `./mvnw -B verify` y Checkstyle, y publica el reporte JaCoCo. `mcp-apps-qa` usa la imagen Playwright Java `v1.64.0-noble`.
- Se añadió `.github/workflows/codeql.yml` con análisis Java/Kotlin en push, PR y schedule semanal.
- Se añadió Playwright Java como dependencia de test y se elevó el umbral de cobertura JaCoCo de 25% a 80%.
- La eliminación permanente de cuenta reasigna el historial contributivo completo al usuario fantasma, incluso si la persona ya había dejado el wallet compartido.
- La ejecución recurrente bloquea y vuelve a validar el registro, evita ejecuciones duplicadas y permite una sola transacción de recuperación cuando el scheduler lleva atraso.
- Se cambió la configuración de Spring de `.properties` a `.yml` para aplicación y tests.
- Se añadieron pruebas para limpieza de idempotencia, renovación de suscripciones, límites de plan y los cambios de cuenta/recurrencias.