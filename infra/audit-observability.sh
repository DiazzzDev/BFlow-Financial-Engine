#!/usr/bin/env bash
# audit-observability.sh
set -uo pipefail
: "${AWS_REGION:?exporta AWS_REGION antes de correr}"
export AWS_DEFAULT_REGION="$AWS_REGION" AWS_DEFAULT_OUTPUT=json
OUT="audit-$(date +%F)"; mkdir -p "$OUT"

run() { local n="$1"; shift; echo ">> $n"; "$@" >"$OUT/$n.json" 2>"$OUT/$n.err" || echo "   falló, ver $OUT/$n.err"; }

run identity       aws sts get-caller-identity --query '{account:Account,arn:Arn}'
run log-groups     aws logs describe-log-groups --query 'logGroups[].{name:logGroupName,retentionDays:retentionInDays,bytes:storedBytes}'
run metric-filters aws logs describe-metric-filters --query 'metricFilters[].{lg:logGroupName,name:filterName,pattern:filterPattern}'
run alarms         aws cloudwatch describe-alarms --query 'MetricAlarms[].{name:AlarmName,ns:Namespace,metric:MetricName,thr:Threshold}'
run dashboards     aws cloudwatch list-dashboards --query 'DashboardEntries[].DashboardName'
run custom-metrics aws cloudwatch list-metrics --query 'Metrics[?!starts_with(Namespace,`AWS/`)].{ns:Namespace,metric:MetricName}'
run sns-topics     aws sns list-topics --query 'Topics[].TopicArn'
run eventbridge    aws events list-rules --query 'Rules[].{name:Name,schedule:ScheduleExpression,pattern:EventPattern}'
run lambdas        aws lambda list-functions --query 'Functions[].{name:FunctionName,runtime:Runtime,mem:MemorySize}'
run rds            aws rds describe-db-instances --query 'DBInstances[].{id:DBInstanceIdentifier,class:DBInstanceClass,engine:Engine,ver:EngineVersion,storageGb:AllocatedStorage}'
run xray-dest      aws xray get-trace-segment-destination

# ECS: servicios y task definitions (solo nombres de env/secrets)
for c in $(aws ecs list-clusters --query 'clusterArns[]' --output text); do
  cn="${c##*/}"
  svcs=$(aws ecs list-services --cluster "$c" --query 'serviceArns[]' --output text)
  [ -n "$svcs" ] && run "ecs-services-$cn" aws ecs describe-services --cluster "$c" --services $svcs \
    --query 'services[].{name:serviceName,desired:desiredCount,running:runningCount,taskDef:taskDefinition,launch:launchType,lb:loadBalancers}'
done

roles=""
for td in $(aws ecs list-task-definition-families --status ACTIVE --query 'families[]' --output text); do
  run "taskdef-$td" aws ecs describe-task-definition --task-definition "$td" --query \
    'taskDefinition.{family:family,cpu:cpu,memory:memory,taskRole:taskRoleArn,execRole:executionRoleArn,containers:containerDefinitions[].{name:name,image:image,cpu:cpu,memory:memory,logs:logConfiguration,envNames:environment[].name,secretNames:secrets[].name}}'
  roles="$roles $(aws ecs describe-task-definition --task-definition "$td" --query 'taskDefinition.[taskRoleArn,executionRoleArn]' --output text)"
done

for arn in $(echo "$roles" | tr ' ' '\n' | grep -v -e '^None$' -e '^$' | sort -u); do
  r="${arn##*/}"
  run "iam-$r-attached" aws iam list-attached-role-policies --role-name "$r"
  run "iam-$r-inline"   aws iam list-role-policies --role-name "$r"
done

# Costo base de CloudWatch, últimos 30 días (Cost Explorer cobra ~$0.01 por llamada)
S="$(date -d '30 days ago' +%F)"; E="$(date +%F)"
run cost-cloudwatch aws ce get-cost-and-usage --time-period Start=$S,End=$E --granularity MONTHLY \
  --metrics UnblendedCost --filter '{"Dimensions":{"Key":"SERVICE","Values":["AmazonCloudWatch"]}}' \
  --group-by Type=DIMENSION,Key=USAGE_TYPE
run cost-by-service aws ce get-cost-and-usage --time-period Start=$S,End=$E --granularity MONTHLY \
  --metrics UnblendedCost --group-by Type=DIMENSION,Key=SERVICE

# Scripts y Terraform existentes en el repo
find . \( -name '*.sh' -o -name '*.tf' \) -not -path './node_modules/*' -not -path './.git/*' > "$OUT/repo-scripts.txt"
echo "Listo. Revisa $OUT/ antes de compartirlo (contiene ARNs y account id)."