# Moderación de imágenes de usuario

## Controles de carga

- Los recibos solo aceptan JPEG, PNG y PDF; las fotos de perfil solo aceptan
  JPEG y PNG, con un máximo actual de 2 MiB. El nombre debe terminar en `.jpg`,
  `.jpeg`, `.png` o `.pdf` según corresponda al MIME declarado. También se
  valida la firma real del contenido para impedir que un ejecutable se disfrace
  como imagen o PDF.
- Las imágenes JPEG y PNG se analizan con Amazon Rekognition antes de que el
  backend las registre o las sirva. Se rechazan etiquetas de desnudez explícita,
  contenido sugerente, violencia, contenido visualmente perturbador y gestos
  obscenos con confianza mínima del 60%.
- El escaneo de fotos de perfil ocurre antes de reemplazar la foto guardada. En
  las cargas S3 presigned, el objeto permanece privado y no pasa a `UPLOADED`
  hasta que `completeUpload` valida su firma y Rekognition lo aprueba. Si el
  análisis rechaza la imagen, el objeto se borra y el registro pasa a `FAILED`.
- Si Rekognition no está disponible, la petición falla cerrada: el contenido no
  se publica ni se registra como listo. Los WebP no se aceptan porque Rekognition
  no puede moderarlos directamente; el cliente debe enviar JPEG o PNG.

## Comprobación de que el archivo sea un recibo

Textract sigue siendo el clasificador de documentos del flujo de recibos. Si no
encuentra señales propias de un recibo (comercio, total, fecha, subtotal,
impuestos o identificador), el trabajo queda `FAILED` y no se presenta como un
borrador de transacción.

Los PDF reciben validación de firma y comprobación de señales de recibo por
Textract, pero Rekognition `DetectModerationLabels` no admite PDF. Por tanto, un
PDF que sí contenga campos de recibo no obtiene análisis visual de desnudez o
violencia página por página. La lista estricta de extensiones evita formatos
activos o no compatibles, pero no sustituye la moderación visual del contenido
interno de un PDF.

## Permisos y configuración

La tarea ECS requiere `rekognition:DetectModerationLabels`; el permiso se limita
a esa operación. S3 permanece privado y Rekognition recibe la referencia del
objeto solo después de una carga autenticada bajo la ruta del usuario. Las
respuestas al cliente son genéricas y no devuelven etiquetas sensibles.
