# Estado de cobertura, mutation testing y distribución de pruebas

**Corte:** 2026-10-28  
**Base:** estado actual del árbol de trabajo; resultados Surefire/JaCoCo de la última ejecución completa de `verify` y reporte PIT de los nueve servicios configurados.

## Resumen ejecutivo

- La ejecución completa contó **345 pruebas**, con **0 fallos, 0 errores y 1 omitida**. Las 344 restantes pasaron.
- JaCoCo reportó **51%** en el log de `verify` (51.8% en el CSV del reporte), por debajo del umbral global configurado de **80%**. Por eso Maven detuvo el ciclo antes de ejecutar PIT en ese `verify`.
- PIT se ejecutó por separado para nueve clases objetivo: **75% de mutaciones**, **311/413**, y **91% de test strength** (311/343 mutantes alcanzados por pruebas). El umbral PIT configurado, 70%, sí se superó.
- La estimación simple para llegar al 80%, manteniendo el denominador actual de 6,821 líneas, es cubrir aproximadamente **1,926 líneas adicionales**. Es una estimación: JaCoCo recalcula el denominador al cambiar código productivo.
- La mayor cantidad de líneas no cubiertas se concentra en `common` (603), `wallet` (520), `budget` (426), `auth` (278) y `subscription` (258). La menor cobertura porcentual entre los paquetes grandes destacados es `storage` (23%), seguida de `notifications` (27.5%).

## Hallazgos de mutation testing

PIT encontró **32 mutantes sobrevivientes** entre los 343 mutantes alcanzados por pruebas; otros 70 de los 413 mutantes contabilizados no fueron alcanzados. Los sobrevivientes no demuestran por sí solos un defecto de producción: señalan conductas que las pruebas no distinguen de la implementación original y que necesitan aserciones o escenarios más discriminantes.

| Área | Ejemplos de mutantes sobrevivientes | Trabajo sugerido |
|---|---|---|
| Wallets (13) | Límites condicionales en resta, ajustes de ingresos/gastos y reversión de impacto; asignación de moneda; inicialización de wallet por defecto; casos de `setOpeningBalance`. | Probar importes iguales, menores y mayores; balances negativos/ceros; wallet propia vs compartida; validar cada campo persistido y el resultado del balance. |
| Recurring (9) | Valor de intervalo/end date al crear; cálculo de múltiplos de fechas; rama de `syncOnUpdate`; descripción, estado activo e intervalo en el template; límite de truncado del fallo. | Afirmar todos los campos de los templates y probar cambios de frecuencia, intervalos y fechas límite adyacentes. |
| Expenses (7) | Adjuntar recibo; ramas de actualización de gasto/categoría/recurrencia; fallback de receipt; categoría y bandera recurrente del quick expense. | Capturar la entidad persistida en create/update, verificar el recibo y cubrir la transición a/desde categoría y recurrencia. |
| Incomes (2) | Categoría y patrón recurrente al actualizar. | Afirmar las propiedades actualizadas y la sincronización del template. |
| Subscription (1) | `nextBillingAt` al cancelar. | Verificar explícitamente la fecha de siguiente cobro y estado después de la cancelación. |

El reporte PIT es **acotado a nueve clases de servicio**; no representa mutación de todo el backend. La tasa por paquete fue: recurring 84% (119/142), subscriptions 92% (34/37), expenses 72% (57/79), incomes 67% (33/49) y wallets 64% (68/106).

## Inventario de pruebas por módulo

Los conteos de pruebas son los casos reportados por Surefire (incluyen el único caso omitido de la clase raíz). La clasificación se basa en el tipo de harness observado:

- **Unitarias:** pruebas de clase/servicio aislado, generalmente con Mockito.
- **Integración:** contexto Spring real o prueba HTTP/validación con MockMvc. `LocaleEndToEndTest` usa MockMvc standalone, no levanta toda la aplicación.
- **E2E:** recorrido coordinado de varias etapas de un flujo. `ReceiptModuleE2ETest` usa repositorio en memoria y mocks en las fronteras de AWS; no es una prueba contra AWS ni una E2E desplegada.
- Una clase fixture de historial en `common/financial` compila, pero no declara métodos `@Test`; no suma casos ejecutados ni aparece en Surefire.

| Módulo | Clases con pruebas ejecutadas | Unitarias | Integración | E2E | Total casos contabilizados |
|---|---:|---:|---:|---:|---:|
| auth | 4 | 7 | 10 | 0 | 17 |
| budget | 6 | 60 | 0 | 0 | 60 |
| common | 10 | 56 | 5 | 0 | 61 |
| dashboard | 1 | 8 | 0 | 0 | 8 |
| expenses | 2 | 24 | 0 | 0 | 24 |
| income | 1 | 11 | 0 | 0 | 11 |
| notifications | 1 | 3 | 0 | 0 | 3 |
| receipts | 1 | 0 | 0 | 15 | 15 |
| recurring | 3 | 42 | 0 | 0 | 42 |
| storage | 1 | 1 | 0 | 0 | 1 |
| subscription | 5 | 33 | 0 | 0 | 33 |
| tranfers (paquete actual) | 1 | 4 | 0 | 0 | 4 |
| transactionimport | 6 | 21 | 0 | 0 | 21 |
| wallet | 3 | 44 | 0 | 0 | 44 |
| **Subtotal de módulos** | **45** | **314** | **15** | **15** | **344** |
| Aplicación (clase raíz) | 1 | 0 | 0 | 0 | 1 omitida |
| **Total Surefire** | **46** | **314** | **15** | **15** | **345** |

`ServiceAccountHardDeleteTest` (7) y `UserServiceDeletionEmailTest` (3) son pruebas de integración Spring en `auth`; `LocaleEndToEndTest` (5) es la prueba MockMvc de `common`; las 15 de `receipts` son el flujo E2E simulado.

## Paquetes con mayor trabajo pendiente según JaCoCo

Ordenados por cantidad de líneas no cubiertas. Los porcentajes son de líneas del paquete y no de mutantes.

| Prioridad | Paquete | Líneas no cubiertas | Cobertura de líneas |
|---:|---|---:|---:|
| 1 | common | 603 | 41.6% |
| 2 | wallet | 520 | 41.0% |
| 3 | budget | 426 | 46.9% |
| 4 | auth | 278 | 41.1% |
| 5 | subscription | 258 | 54.7% |
| 6 | storage | 164 | 23.0% |
| 7 | notifications | 132 | 27.5% |
| 8 | transactionimport | 117 | 67.0% |
| 9 | dashboard | 98 | 73.6% |
| 10 | tranfers | 98 | 35.9% |
| 11 | expenses | 96 | 73.6% |
| 12 | income | 91 | 65.3% |
| 13 | recurring | 84 | 78.9% |
| 14 | receipts | 92 | 80.9% |

**Orden práctico para distribuir la carga:**

1. Dividir `common`, `wallet`, `budget`, `auth` y `subscription` por servicios o controladores con lógica de negocio y ramas; empezar por los flujos críticos y los mayores huecos de JaCoCo.
2. Dar prioridad a los sobrevivientes PIT de `wallet`, `recurring`, `expenses` e `income`, porque corresponden a límites financieros y asignación de campos que las pruebas actuales no discriminan.
3. Cubrir `storage` y `notifications` con escenarios de error y fallback: su porcentaje es bajo aunque su volumen absoluto sea menor.
4. Después completar los huecos de `transactionimport`, `dashboard`, `tranfers` y los restantes, manteniendo ambos umbrales.

## Por qué `./mvnw -B verify` no corre en este workspace

Se ejecutó el comando exacto en `/workspaces/BFlow-Backend`. **No alcanza a compilar ni ejecutar pruebas.** Falla al filtrar `src/main/resources/application.yml` hacia:

```text
target/classes/application.yml: Operation not permitted
Failed to execute goal ... maven-resources-plugin ... resources
```

La causa es de permisos del directorio de build, no del POM ni de la aplicación:

```text
usuario actual: vscode (uid 1000)
target:         root:root, modo 755
target/classes: root:root, modo 755
```

Aunque el archivo `target/classes/application.yml` tiene permisos de escritura, el proceso necesita escribir/reemplazar archivos dentro de `target/classes`; `vscode` no tiene permiso de escritura en el directorio, propiedad de `root`.

**Alternativas seguras:** ejecutar Maven en un checkout/copia con `target` escribible, o pedir al administrador del workspace que corrija la propiedad/permisos de **este `target` específico**. No se debe bajar el coverage ni omitir fases. En esta sesión, la verificación completa se hizo en una copia temporal escribible; allí terminaron las pruebas, pero `verify` se detuvo después en el umbral JaCoCo de 80% (resultado 51%).

