# Diagnóstico: afiliación recurrente de Wompi y activación de Pro

## Resultado observado

El 29 de octubre de 2026 el cliente completó la pantalla de Wompi y Wompi
mostró que la suscripción recurrente fue creada. El registro local
`ffe1bb4f-1083-4bc9-af77-aecdc7328967` quedó en `PENDING_ACTIVATION` con el
enlace recurrente `914394f0-0dab-483d-bce0-e360135a7dbb`, día de cobro 28 y
monto USD 9.99.

CloudWatch confirma solamente la creación del enlace y del registro local. No
hay un evento de cobro aprobado posterior. Esto es coherente con el flujo de
Wompi: la pantalla confirma que la tarjeta fue afiliada al cargo recurrente;
no confirma que ya se cobró el primer pago. Wompi documenta que el enlace
recurrente afilia al cliente para cobrarle cada mes en el día configurado, y
que los webhooks notifican transacciones exitosas, no el alta de la tarjeta.

El día configurado era el 28 y la afiliación ocurrió después del 28 de octubre.
Por ello el primer cargo se espera en el siguiente ciclo, el 28 de noviembre.
El valor `estaProductivo: false` de CloudWatch también confirma que se usó el
aplicativo de prueba: no representa un cargo financiero real.

Fuentes de Wompi:

- [Enlaces de pago recurrentes](https://docs.wompi.sv/metodos-api/crear-enlace-pago-recurrentes)
- [Definición de webhook](https://docs.wompi.sv/webhook/definicion-webhook)
- [Validación del hash del webhook](https://docs.wompi.sv/webhook/validar-webhook)

## Causa raíz

La reconciliación local consultaba correctamente los suscriptores de Wompi,
pero solo activaba el plan cuando el suscriptor estaba `Activa` **y**
`pagosRealizados >= 1`. Esa segunda condición impide otorgar Pro después de
que Wompi acepta la afiliación, porque no existe un pago hasta el próximo día
de cobro.

Además, un suscriptor todavía no encontrado se marcaba `EXPIRED` en el primer
ciclo de reconciliación. El trabajo se ejecutaba cada seis horas. Esto era
incompatible con un enlace que el cliente puede terminar de aceptar más tarde
y con una afiliación cuyo primer cobro será en el siguiente mes.

No hay evidencia de un webhook rechazado en la captura. La ausencia de webhook
inmediatamente después de la pantalla de alta es normal: Wompi envía ese POST
para una transacción exitosa. El endpoint mantiene la validación HMAC del
header `wompi_hash` antes de aceptar un pago.

## Solución aplicada

1. Una afiliación de Wompi con estado `Activa` ahora activa la suscripción de
   BFlow aunque `pagosRealizados` sea cero.
2. Esta activación guarda el identificador del suscriptor, programa el próximo
   cobro y cancela el plan activo anterior del mismo usuario.
3. No se crea un pago sintético: el registro `payments` se crea únicamente
   cuando Wompi envía una transacción aprobada y firmada.
4. El reconciliador corre cada 15 minutos y conserva una afiliación sin
   completar durante 30 días antes de expirar.
5. Las nuevas suscripciones mensuales envían a Wompi un nombre y una
   descripción generados desde los límites reales del plan. También adjuntan
   el logo configurado en `WOMPI_PRODUCT_IMAGE_URL`; si no se define, reutiliza
   `LOGO_URL`. Un fallo de carga de imagen no impide crear el checkout.

## Validación automatizada

Las pruebas cubren que:

- una afiliación `Activa` con cero pagos activa Pro;
- la comparación de correo no depende de mayúsculas;
- una afiliación incompleta no expira durante los primeros 30 días;
- una afiliación ausente expira después de ese periodo;
- la activación por afiliación no crea un pago falso;
- una suscripción anterior se cancela al activar la nueva;
- `PRO_MONTHLY` presenta sus cinco límites y sus tres capacidades extra en
  lenguaje natural;
- el checkout mensual envía la descripción y solicita la imagen de marca.

## Acción posterior al despliegue

Después de desplegar, el registro pendiente actual se reconciliará en el
siguiente ciclo de hasta 15 minutos. Si Wompi devuelve el suscriptor con
estado `Activa`, pasará a `ACTIVE` y conservará como próximo cobro el siguiente
día 28. Si no aparece como activo en Wompi, se mantiene pendiente para que se
pueda revisar la afiliación sin revocar el enlace prematuramente.
