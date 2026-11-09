# Gmail: resumen para promoción a `main`

Fecha: 9 de noviembre de 2026

## Alcance incluido

- OAuth de Gmail separado del login, con permiso de solo lectura `gmail.readonly`.
- Refresh tokens cifrados en AWS KMS; revocación de Google con reintentos y eliminación local de credenciales solo tras éxito.
- Sincronización asíncrona con límites, validación de adjuntos y una decisión de importación por mensaje: PDF compatible, imagen compatible y texto del correo solo si no hay adjunto compatible.
- Flujo S3 temporal y privado conectado al procesamiento compartido de Textract mediante SQS/SNS.
- Detección de duplicados por mensaje; guarda estado técnico para poder auditar el resultado.
- Registro como ingreso o gasto y notificación al usuario.
- Exportación autenticada `GET /api/v1/users/me/export` en JSON: perfil, membresías y movimientos aportados por el usuario; no incluye tokens OAuth ni datos de otros miembros.
- Políticas ES/EN ajustadas y fechadas al 9 de noviembre de 2026; la versión española prevalece.
- Terraform, despliegue y pruebas del backend relacionados con la integración.

## Verificación ejecutada

- `mvn -DskipTests compile`: BUILD SUCCESS.
- Pruebas Gmail: 15 ejecutadas, 0 fallidas, 0 errores.

## Pasos de publicación/revisión Google

1. Promover `develop` a `main` mediante el flujo normal del repositorio.
2. Desplegar las páginas legales en el dominio público y comprobar que las URLs de privacidad y términos abren sin autenticación.
3. Configurar en Google Auth Platform las URLs públicas, el dominio verificado, contactos de soporte/desarrollador, redirect URI de producción y el scope declarado.
4. Adjuntar justificación del scope y video de demostración del consentimiento y uso Gmail.
5. Confirmar en el Verification Center la clasificación y evaluación de seguridad requerida por Google para `gmail.readonly`.

El push a `develop` no despliega la aplicación ni actualiza automáticamente las páginas públicas de producción.
