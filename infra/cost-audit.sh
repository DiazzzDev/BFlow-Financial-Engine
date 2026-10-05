#!/usr/bin/env bash
# =============================================================================
# BFlow - Auditoria de costos AWS (SOLO LECTURA)
#
# Uso:   bash cost-audit.sh
# Salida: cost-audit-YYYYMMDD-HHMM.txt (tambien se imprime en pantalla)
#
# Correr con tus credenciales de admin/personales, NO con el rol de GitHub.
# No imprime valores de secrets ni variables de entorno de las task definitions.
# Cada llamada a Cost Explorer cuesta USD 0.01 (este script hace ~8).
# =============================================================================
set -uo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash en Windows
export AWS_PAGER=""

REGION="${AWS_REGION:-us-east-1}"
OUT="cost-audit-$(date -u +%Y%m%d-%H%M).txt"

MONTH_START=$(date -u +%Y-%m-01)
TOMORROW=$(date -u -d tomorrow +%F)
THREE_AGO=$(date -u -d "${MONTH_START} -3 months" +%F)
DAYS14_AGO=$(date -u -d "14 days ago" +%Y-%m-%dT00:00:00Z)
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
DAILY_START=$(date -u -d "14 days ago" +%F)

section() { printf '\n\n===== %s =====\n' "$1"; }
run()     { printf '\n$ %s\n' "$*"; "$@" 2>&1 | tr -d '\r'; [ "${PIPESTATUS[0]}" -eq 0 ] || echo "(el comando fallo o no aplica)"; }
awsq()    { aws "$@" 2>/dev/null | tr -d '\r'; }   # para capturar en variables

ce_usage() {  # $1 = nombre del servicio en Cost Explorer
  run aws ce get-cost-and-usage \
    --time-period Start="$MONTH_START",End="$TOMORROW" \
    --granularity MONTHLY \
    --metrics UnblendedCost UsageQuantity \
    --group-by Type=DIMENSION,Key=USAGE_TYPE \
    --filter "{\"Dimensions\":{\"Key\":\"SERVICE\",\"Values\":[\"$1\"]}}" \
    --query 'ResultsByTime[].Groups[].[Keys[0],Metrics.UnblendedCost.Amount,Metrics.UsageQuantity.Amount]' \
    --output text --region us-east-1
}

{
echo "BFlow cost audit - $(date -u +%FT%TZ) - region $REGION"

# -----------------------------------------------------------------------------
section "0. IDENTIDAD"
run aws sts get-caller-identity --output json

# -----------------------------------------------------------------------------
section "1. COSTOS POR SERVICIO (ultimos 3 meses + mes actual)"
run aws ce get-cost-and-usage \
  --time-period Start="$THREE_AGO",End="$TOMORROW" \
  --granularity MONTHLY --metrics UnblendedCost \
  --group-by Type=DIMENSION,Key=SERVICE \
  --query 'ResultsByTime[].[TimePeriod.Start,Groups[].[Keys[0],Metrics.UnblendedCost.Amount]]' \
  --output json --region us-east-1

section "1b. COSTO DIARIO TOTAL (ultimos 14 dias)"
run aws ce get-cost-and-usage \
  --time-period Start="$DAILY_START",End="$TOMORROW" \
  --granularity DAILY --metrics UnblendedCost \
  --query 'ResultsByTime[].[TimePeriod.Start,Total.UnblendedCost.Amount]' \
  --output text --region us-east-1

section "1c. DETALLE POR TIPO DE USO (mes actual): usage_type / USD / cantidad"
for SVC in \
  "Amazon Elastic Container Service" \
  "Amazon Virtual Private Cloud" \
  "AWS Secrets Manager" \
  "Amazon EC2 Container Registry (ECR)" \
  "AmazonCloudWatch"; do
  echo; echo "--- $SVC ---"
  ce_usage "$SVC"
done

# -----------------------------------------------------------------------------
section "2. ECS: clusters, servicios, tamano de tareas y utilizacion (14 dias)"
run aws ecs list-clusters --region "$REGION" --output text

for C in $(awsq ecs list-clusters --region "$REGION" --query 'clusterArns[]' --output text); do
  CN="${C##*/}"
  echo; echo "########## CLUSTER: $CN"
  SVCS=$(awsq ecs list-services --cluster "$CN" --region "$REGION" --query 'serviceArns[]' --output text)
  if [ -z "$SVCS" ]; then echo "(sin servicios)"; continue; fi

  # shellcheck disable=SC2086
  run aws ecs describe-services --cluster "$CN" --services $SVCS --region "$REGION" \
    --query 'services[].{svc:serviceName,desired:desiredCount,running:runningCount,launchType:launchType,capacityProvider:capacityProviderStrategy,publicIp:networkConfiguration.awsvpcConfiguration.assignPublicIp,minHealthy:deploymentConfiguration.minimumHealthyPercent,maxPercent:deploymentConfiguration.maximumPercent,taskDef:taskDefinition}' \
    --output json

  for S in $SVCS; do
    SN="${S##*/}"
    echo; echo "---------- SERVICIO: $SN"
    TD=$(awsq ecs describe-services --cluster "$CN" --services "$SN" --region "$REGION" \
          --query 'services[0].taskDefinition' --output text)
    run aws ecs describe-task-definition --task-definition "$TD" --region "$REGION" \
      --query 'taskDefinition.{family:family,cpu:cpu,memory:memory,platform:runtimePlatform,containers:containerDefinitions[].{name:name,cpu:cpu,memory:memory,memoryReservation:memoryReservation}}' \
      --output json
    for M in CPUUtilization MemoryUtilization; do
      echo; echo "$M diario (timestamp / promedio % / maximo %):"
      run aws cloudwatch get-metric-statistics --namespace AWS/ECS --metric-name "$M" \
        --dimensions Name=ClusterName,Value="$CN" Name=ServiceName,Value="$SN" \
        --start-time "$DAYS14_AGO" --end-time "$NOW" --period 86400 \
        --statistics Average Maximum \
        --query 'sort_by(Datapoints,&Timestamp)[].[Timestamp,Average,Maximum]' \
        --output text --region "$REGION"
    done
  done
done

# -----------------------------------------------------------------------------
section "3. VPC / RED: lo que genera cobros"
echo "# IPs publicas en uso por interfaces de red (cada IPv4 publica ~ USD 3.60/mes)"
run aws ec2 describe-network-interfaces --region "$REGION" \
  --query 'NetworkInterfaces[?Association.PublicIp!=`null`].[NetworkInterfaceId,Association.PublicIp,Description,Status]' \
  --output text
echo "# Elastic IPs"
run aws ec2 describe-addresses --region "$REGION" \
  --query 'Addresses[].[PublicIp,AllocationId,AssociationId,NetworkInterfaceId]' --output text
echo "# NAT gateways"
run aws ec2 describe-nat-gateways --region "$REGION" \
  --query 'NatGateways[].[NatGatewayId,State,VpcId]' --output text
echo "# VPC endpoints (los de tipo Interface cobran por hora)"
run aws ec2 describe-vpc-endpoints --region "$REGION" \
  --query 'VpcEndpoints[].[VpcEndpointId,VpcEndpointType,ServiceName,State]' --output text
echo "# Load balancers"
run aws elbv2 describe-load-balancers --region "$REGION" \
  --query 'LoadBalancers[].[LoadBalancerName,Type,State.Code]' --output text

# -----------------------------------------------------------------------------
section "4. SECRETS MANAGER (solo metadata, sin valores)"
run aws secretsmanager list-secrets --region "$REGION" --include-planned-deletion \
  --query 'SecretList[].[Name,LastAccessedDate,LastChangedDate,DeletedDate]' --output text

# -----------------------------------------------------------------------------
section "5. ECR: repos, cantidad y tamano de imagenes, lifecycle policy"
for R in $(awsq ecr describe-repositories --region "$REGION" --query 'repositories[].repositoryName' --output text); do
  echo; echo "--- repo: $R  (cantidad de imagenes / bytes totales)"
  run aws ecr describe-images --repository-name "$R" --region "$REGION" \
    --query '[length(imageDetails),sum(imageDetails[].imageSizeInBytes)]' --output text
  echo "lifecycle policy:"
  run aws ecr get-lifecycle-policy --repository-name "$R" --region "$REGION" \
    --query 'lifecyclePolicyText' --output text
done

# -----------------------------------------------------------------------------
section "6. CLOUDWATCH LOGS (retencion vacia/None = nunca expira)"
run aws logs describe-log-groups --region "$REGION" \
  --query 'logGroups[].[logGroupName,retentionInDays,storedBytes]' --output text

# -----------------------------------------------------------------------------
section "7. OTROS RECURSOS (SQS, SNS, buckets S3)"
run aws sqs list-queues --region "$REGION" --output text
run aws sns list-topics --region "$REGION" --query 'Topics[].TopicArn' --output text
run aws s3api list-buckets --query 'Buckets[].[Name,CreationDate]' --output text

echo; echo "===== FIN ====="
} 2>&1 | tee "$OUT"

echo
echo "Listo. Reporte guardado en: $OUT"
echo "Contiene ID de cuenta y nombres de recursos, pero ningun valor de secrets."
