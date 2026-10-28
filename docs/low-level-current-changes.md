# Cambios actuales: detalle de bajo nivel

**Estado de referencia:** `develop` en `f39f068` (`2026-10-22`) más los cambios locales visibles al `2026-10-28`. Este documento describe el estado del worktree; no implica que los cambios locales estén committeados ni que las verificaciones hayan pasado.

## CI y seguridad

- `.github/workflows/ci.yml` se reorganizó en tres jobs:
  - `verify`: ejecuta `./mvnw -B verify`, Checkstyle y publica `target/site/jacoco/` como artefacto `jacoco-report`.
  - `mcp-apps-qa`: usa `mcr.microsoft.com/playwright/java:v1.64.0-noble` y ejecuta `./mvnw -B -P mcp-apps-qa test`.
  - `security`: reutiliza `.github/workflows/security.yml`.
- El job `verify` conserva el servicio PostgreSQL 16 y las variables de entorno ficticias requeridas por la configuración de pruebas del backend.
- `.github/workflows/codeql.yml` añade análisis Java/Kotlin en push y pull request a `main` y `develop`, además de una ejecución semanal. Inicializa CodeQL en modo de build manual, compila con Java 21/Maven y publica el análisis.
- `.github/workflows/infra-ci.yml` ya valida `terraform fmt` y `terraform validate` en pull requests que tocan `infra/**`; no se modificó en este conjunto local.
- La suite de tests no contiene actualmente un perfil Maven `mcp-apps-qa` ni clases de prueba MCP Apps/Playwright identificables. El job existe, pero el perfil aún debe implementarse para asegurar que ejecute una suite específica del harness.

## Dependencias y cobertura

- `pom.xml` añade `com.microsoft.playwright:playwright:1.58.0` con scope `test`.
- La cobertura mínima de líneas JaCoCo cambia de `0.25` a `0.80` a nivel bundle.
- `mvnw.cmd` presenta cambios de formato de fin de línea; no se observa un cambio funcional en el script respecto al archivo base.

## Eliminación permanente de cuenta

- `ServiceAccountHardDelete` deja de limitar la reasignación del historial a los wallets compartidos que todavía aparecen como membresías al momento del borrado.
- Ahora reasigna al usuario fantasma todas las contribuciones existentes de gastos, ingresos y transferencias, todos los registros de recibos asociados al usuario y la propiedad de archivos de recibos que sigan referenciados por gastos.
- Se agregan/renombran consultas de repositorio (`reassignAllContributions`, `reassignAllUsers`, `reassignAllReceiptOwners`) para quitar el filtro por IDs de wallets.
- `ServiceAccountHardDeleteTest` agrega el caso de un usuario que ya salió de un wallet compartido, pero conserva actividad histórica en él; verifica que el borrado reasigne el gasto histórico y elimine la cuenta.

## Ejecución de transacciones recurrentes

- `RepositoryRecurringTransaction` añade `findByIdForUpdate`, que recarga el registro con bloqueo pesimista.
- `RecurringTransactionExecutor.executeSingle` usa ese bloqueo en la transacción aislada (`REQUIRES_NEW`) y vuelve a validar que la recurrencia siga activa, vencida y dentro de su fecha final antes de crear una transacción.
- Tras ejecutar una ocurrencia atrasada, el cálculo avanza la siguiente fecha más allá de hoy para permitir una sola recuperación y evitar reproducir cada ocurrencia perdida en los siguientes ciclos del scheduler. El avance cubre frecuencias diaria, semanal, mensual y anual.
- Se amplían las pruebas del executor para validar lock lookup, catch-up, evitar doble ejecución y respetar la fecha final; se amplían pruebas del servicio de ejecución para errores y fallos de correo sin interrumpir el lote.

## Configuración Spring

- `src/main/resources/application.properties` se sustituye por `application.yml`; `src/test/resources/application-test.properties` se sustituye por `application-test.yml`.
- La configuración YAML conserva la conexión PostgreSQL y agrega/ordena ajustes de Hibernate, Flyway, multipart, AWS, Firebase, Wompi, actuator, logs JSON de acceso y campos `trace_id`/`span_id`.
- La configuración de test mantiene PostgreSQL y `ddl-auto: validate`.

## Pruebas nuevas locales

- `common/idempotency/scheduler/IdempotencyCleanupTaskTest`: prueba del proceso de limpieza de claves de idempotencia.
- `subscription/scheduler/SubscriptionRenewalSchedulerTest`: prueba de vencimientos, recordatorios de renovación y delegación de reconciliación.
- `subscription/services/PlanLimitServiceTest`: prueba de límites, funcionalidades habilitadas/deshabilitadas y usuarios sin suscripción activa.

## Límites de verificación

- No se ejecutaron pruebas ni workflows durante la preparación de este documento.
- Debe confirmarse que el perfil `mcp-apps-qa` y sus pruebas existen antes de confiar en ese job como gate específico del harness MCP Apps.
