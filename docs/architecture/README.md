# Arquitectura AWS de BFlow

El [diagrama interactivo](index.html) separa los servicios de la aplicación, el flujo de OCR de recibos y el proceso de publicación/actualización DNS. El HTML contiene los SVG, los íconos oficiales necesarios y el comportamiento de tooltips, idioma y tema; se puede abrir sin dependencias externas. Los íconos fuente utilizados se conservan en `icons/`.

El inventario se contrastó con `.github/workflows/deploy.yml` para explicar las conexiones de despliegue y distinguir recursos creados por el workflow de los que administra Terraform. Los tooltips del diagrama indican qué hace cada servicio, los archivos `.tf` que lo definen y sus conexiones.

Los SVG fuente son de [AWS Architecture Icons](https://aws.amazon.com/architecture/icons/) (paquete Q3 2026); las copias usadas se conservan en `icons/` y se incrustan en el HTML.
Los logotipos de [GitHub](https://brand.github.com/foundations/logo) y [Cloudflare](https://www.cloudflare.com/logo/) del flujo también se conservan en `icons/` y se incrustan en el HTML.

## Flujo de OCR de recibos

1. La API en ECS guarda el archivo original en el bucket S3 privado y registra la solicitud.
2. La aplicación envía el ID del recibo a la cola SQS de solicitudes cifrada.
3. El worker ECS resuelve la clave del objeto y llama a la API asíncrona `AnalyzeExpense` de Textract con ese objeto S3.
4. Textract publica la finalización en SNS; la suscripción entrega el aviso a la cola SQS de resultados.
5. El worker ECS consulta `GetExpenseAnalysis`, mapea los campos extraídos y actualiza el estado del recibo para que el usuario revise la sugerencia.

Terraform crea S3, colas/SNS y el rol que permite a Textract publicar. **Textract es una integración con una API administrada de AWS, no un recurso aprovisionado por este Terraform.** Las DLQ reciben mensajes que superan el máximo de reintentos.

## Sincronización DNS y costos

El dominio `bflow-studio.com` se compró en Cloudflare, que ofrece gestión gratuita de registros DNS. La opción de sincronización actualiza directamente la IP pública de la tarea ECS y evita el costo de mantener un Application Load Balancer (ALB). Debe existir previamente un registro A en Cloudflare; Lambda solo actualiza su contenido.

La Lambda consulta ECS para identificar la tarea RUNNING más nueva y llama a la API `DescribeNetworkInterfaces` (EC2) para obtener la IP pública de su ENI. **No se ejecuta una instancia EC2** para este proceso; EC2 aparece solo como API de plano de control. Esta sincronización es opcional y está desactivada por defecto.

## Servicio → responsabilidad

| Servicio / recurso | Responsabilidad en BFlow | IaC / condición |
| --- | --- | --- |
| Amazon VPC | Red principal `10.0.0.0/16` con DNS habilitado. | `network.tf` · `aws_vpc.main` |
| Internet Gateway | Ruta pública de Internet para las subredes públicas. | `network.tf` · `aws_internet_gateway.main` y route table pública |
| Subredes públicas (2 AZ) | Subredes con asignación automática de IP pública y asociación a la route table pública. | `network.tf` · `aws_subnet.public` (count 2) |
| Subredes privadas (2 AZ) | Subredes privadas y route tables sin ruta por defecto a Internet. | `network.tf` · `aws_subnet.private`, `aws_route_table.private` (count 2) |
| Security groups ECS/RDS | Reglas de entrada configurables para ECS; RDS admite PostgreSQL desde el SG de ECS. | `network.tf` · ECS siempre; RDS solo con `db_provider=rds` |
| Amazon ECS cluster | Agrupa las tareas del backend; Container Insights está deshabilitado. | `storage-and-compute.tf` · `aws_ecs_cluster.main` |
| ECS Fargate service/task | Ejecuta la API en las dos subredes públicas; el workflow registra la task definition y crea/actualiza el service. | `.github/workflows/deploy.yml` · no declarado como recurso Terraform |
| Amazon ECR | Repositorio privado e inmutable para imágenes del backend; escaneo al push. | `storage-and-compute.tf` · `aws_ecr_repository.backend`; lifecycle policy opcional |
| Amazon S3 | Archivos de la aplicación; bloquea acceso público, cifra con AES-256, configura lifecycle/CORS. | `storage-and-compute.tf` · `aws_s3_bucket.files` y recursos asociados |
| AWS Secrets Manager | Contenedores para secretos de DB, Wompi y Firebase; opcionalmente Cloudflare. Los valores se gestionan fuera del estado Terraform. | `storage-and-compute.tf`; `dns-sync.tf` para Cloudflare opcional |
| Amazon SQS | Colas OCR de solicitudes/resultados, dos DLQ, cifrado administrado por SQS y policies. | `iam-and-messaging.tf` |
| Amazon SNS | Topic de resultados OCR con suscripción SQS, y topic de alarmas con suscripción email. | `iam-and-messaging.tf`; `observability.tf` |
| AWS Lambda | Función Go para sincronización DNS basada en IP pública de las tareas ECS. | `dns-sync.tf` · solo con `dns_sync_enabled=true` y ZIP configurado; default `false` |
| Amazon EventBridge | Reglas ECS para invocar DNS sync al iniciar una tarea y publicar fallos a SNS. | `dns-sync.tf` opcional; `observability.tf` para fallos de tareas |
| Amazon CloudWatch | Log groups, filtros de métricas, alarmas HTTP/latencia/CPU/memoria y dashboard. | `storage-and-compute.tf`; `dns-sync.tf`; `observability.tf` |
| AWS IAM | Roles/policies de ejecución y task de ECS, Textract, DNS Lambda y GitHub Actions; OIDC provider de GitHub. | `iam-and-messaging.tf`; `dns-sync.tf`; `github-and-outputs.tf` |
| Amazon Cognito User Pool Client | Cliente OAuth móvil vinculado a un user pool existente. | `github-and-outputs.tf` · solo con `manage_cognito_client=true` y `cognito_user_pool_id` |
| Amazon RDS PostgreSQL | Base de datos opcional, cifrada, privada y con subnet group en ambas subredes privadas. | `storage-and-compute.tf` y `network.tf` · solo con `db_provider=rds`; default `supabase` |
| AWS Budgets | Presupuesto de coste mensual; notificaciones al 50/80/100% si se configuran emails. | `storage-and-compute.tf` · `aws_budgets_budget.monthly` |
| GitHub Actions + OIDC | Valida el entorno, construye y publica la imagen, registra task definition y despliega ECS sin claves AWS persistentes. | `.github/workflows/deploy.yml`; el provider/rol OIDC está en `github-and-outputs.tf` |
| Cloudflare DNS | Guarda el A record configurado (por ejemplo `bflow-studio.com`) hacia la IP pública actual de la tarea. | Servicio externo; opcional `dns-sync.tf` + `infra/dns-sync/main.go`; el nombre/ID/zone/token vienen de `CLOUDFLARE_DNS_RECORD_NAME` y otros campos del secreto `bflow/cloudflare-dns` |

## No encontrado en IaC

Estos elementos se muestran como pasos externos del workflow o se documentan aquí, pero no se crean como recursos AWS de Terraform:

- **ECS service y task definition:** aparecen en el flujo de publicación porque `.github/workflows/deploy.yml` genera la definición, registra tareas y crea/actualiza el servicio. Terraform declara solo el cluster y roles relacionados.
- **Base de datos Supabase:** es el proveedor por defecto (`db_provider=supabase`), pero el proyecto Supabase y su base de datos son externos a este Terraform.
- **Amazon Cognito User Pool:** el Terraform puede crear un cliente OAuth para un pool preexistente; el pool no se crea aquí.
- **Amazon Textract:** las colas, topic y roles/permisos para iniciar y leer análisis sí están en IaC; el servicio/API Textract no se aprovisiona como recurso Terraform.
- **Amazon SES:** la aplicación y el workflow usan la configuración de correo, pero no hay un recurso SES ni una policy SES en `infra/terraform/`.
- **AWS X-Ray:** el task role permite `xray:PutTraceSegments` y `deploy.yml` configura el agente/exportador; no hay recurso X-Ray definido en estos `.tf`.
- **Cloudflare DNS zone y records:** el proceso opcional actualiza por Cloudflare API el A record configurado; para el dominio raíz, `CLOUDFLARE_DNS_RECORD_NAME` debe ser `bflow-studio.com`. El nombre, zone ID, record ID y token están en `bflow/cloudflare-dns`; la zona/record no son recursos Terraform.
- **NAT Gateway y Application Load Balancer:** no hay recursos Terraform de NAT ni ALB; las route tables privadas tampoco declaran salida a Internet.
- **GitHub Actions como servicio de ejecución:** el workflow forma parte del proceso de despliegue, no es un recurso AWS de Terraform. Sí se declara su proveedor OIDC y rol IAM.

## Archivos fuente

- `infra/terraform/network.tf`
- `infra/terraform/storage-and-compute.tf`
- `infra/terraform/iam-and-messaging.tf`
- `infra/terraform/dns-sync.tf`
- `infra/terraform/observability.tf`
- `infra/terraform/github-and-outputs.tf`
- `.github/workflows/deploy.yml`

# BFlow architecture overview

View the live architecture diagram in [index.html](index.html). The diagram's header links to the BFlow application at [www.bflow-studio.com](https://www.bflow-studio.com/).

The repository publishes this directory to GitHub Pages with [architecture-pages.yml](../../.github/workflows/architecture-pages.yml). Configure the repository's Pages source as **GitHub Actions** once; pushes to `main` that change this directory publish the static site. See [GITHUB-PAGES.md](GITHUB-PAGES.md) for setup and custom-domain guidance.
