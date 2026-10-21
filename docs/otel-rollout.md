# OTEL rollout runbook

## Scope

This runbook is meant for the production rollout of the ADOT Java agent for the BFlow backend running in ECS/Fargate. It is intentionally operational and does not modify domain logic.

## Preconditions

- Transaction Search in AWS X-Ray is enabled in the AWS console.
- The ECS service is healthy and the task definition is already using the standard production environment variables.
- The task role has the `bflow-otel-traces` inline policy (X-Ray write), applied with `terraform apply`.
- The CloudWatch log group is `/ecs/bflow`.

## Enablement summary

The rollout gate is the `OTEL_ENABLED` GitHub Actions variable (`vars.OTEL_ENABLED`), which the deploy workflow uses to render the ECS task definition. When `OTEL_ENABLED=true`, the container receives:

- `JAVA_TOOL_OPTIONS=-javaagent:/opt/otel/aws-opentelemetry-agent.jar`
- `OTEL_SERVICE_NAME=bflow-backend`
- `OTEL_RESOURCE_ATTRIBUTES=deployment.environment=prod`
- `OTEL_TRACES_EXPORTER=otlp`
- `OTEL_EXPORTER_OTLP_TRACES_PROTOCOL=http/protobuf`
- `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=https://xray.us-east-1.amazonaws.com/v1/traces`
- `OTEL_METRICS_EXPORTER=none`
- `OTEL_LOGS_EXPORTER=none`
- `OTEL_AWS_APPLICATION_SIGNALS_ENABLED=false`
- `OTEL_TRACES_SAMPLER=parentbased_traceidratio`
- `OTEL_TRACES_SAMPLER_ARG=<OTEL_SAMPLE_RATIO>`
- `OTEL_INSTRUMENTATION_COMMON_DB_STATEMENT_SANITIZER_ENABLED=true`

## Before deploy

Run these after enabling Transaction Search in the console. Retention of `/ecs/bflow` is managed by Terraform (`log_retention_days`), so it is not set here.

```bash
aws xray get-trace-segment-destination
aws xray update-indexing-rule --name Default \
  --rule '{"Probabilistic":{"DesiredSamplingPercentage":1}}'
aws logs put-retention-policy --log-group-name aws/spans --retention-in-days 7
aws logs describe-log-streams --log-group-name /ecs/bflow --order-by LastEventTime --descending --max-items 5
```

Expected preconditions:

- `aws xray get-trace-segment-destination` should report `CloudWatchLogs` after Transaction Search is enabled.
- CloudWatch log group `/ecs/bflow` should exist.
- The log stream for `/ecs/bflow` should be active before the first traces arrive.

## Deploy

The switch is the `OTEL_ENABLED` repository variable read by the GitHub Actions workflow. It is not an environment variable of your shell, and the GitHub CLI is not required.

1. In GitHub: repository Settings -> Secrets and variables -> Actions -> Variables tab. Create or edit `OTEL_ENABLED` (`true` to enable, `false` to roll back). Put it where the other variables (`S3_BUCKET`, `AWS_REGION`, ...) live: repository level or the `production` environment.
2. Optional: set `OTEL_SAMPLE_RATIO` (default `0.1`) in the same place.
3. Apply the X-Ray policy once: `cd infra/terraform && terraform plan && terraform apply`.
4. Roll out in this order:
   1. Deploy with `OTEL_ENABLED=false` first. This validates the image, the access log and the task definition with the agent inert.
   2. Enable Transaction Search in the console and run the commands from "Before deploy".
   3. Set `OTEL_ENABLED=true` and re-run the deploy workflow in a low-traffic window, then wait for the service to be stable.

## Post-deploy checks

```bash
aws ecs describe-services --cluster bflow-cluster --services bflow-service
aws ecs list-service-deployments --cluster bflow-cluster --service bflow-service
aws logs tail /ecs/bflow --follow
aws logs tail aws/spans --follow
```

### Look for exporter faults

```bash
aws logs tail /ecs/bflow --follow --since 30m | grep -Ei 'otel|opentelemetry|otlp|xray|401|403|timeout'
```

Look for:

- `401` or `403` from the exporter endpoint
- repeated timeouts or retries
- `xray` or `otlp` warnings in the container logs
- service restarts after the new task definition is registered

## Trace discovery

Use a request `trace_id` from `/ecs/bflow` and then search in X-Ray and CloudWatch Logs.

```bash
# Example: from the application logs or the access log JSON
aws logs tail /ecs/bflow --follow | grep 'trace_id='

# In X-Ray console: Service map -> Trace -> request trace ID
```

## Cost and impact tracking

### Before vs after

```bash
# 24h before vs 24h after, grouped by service:
aws ce get-cost-and-usage \
  --time-period Start=$(date -u -d '48 hours ago' +%F),End=$(date -u +%F) \
  --granularity DAILY \
  --metrics BlendedCost \
  --group-by Type=DIMENSION,Key=SERVICE \
  --filter '{"Not":{"Dimensions":{"Key":"RECORD_TYPE","Values":["Credit","Refund"]}}}'

aws logs describe-log-groups --log-group-name-prefix 'aws/spans'
```

Also compare:

- `AWS/ECS` CPUUtilization / MemoryUtilization for `bflow-cluster` and service `bflow-service`
- CloudWatch log volume for `/ecs/bflow` and `aws/spans`
- `storedBytes` and `IncomingBytes` for the `aws/spans` log group before and after rollout

### Commands for the comparison

```bash
# CPU and memory over the last 24h (run it before and after the change)
for M in CPUUtilization MemoryUtilization; do
  aws cloudwatch get-metric-statistics --namespace AWS/ECS --metric-name "$M" \
    --dimensions Name=ClusterName,Value=bflow-cluster Name=ServiceName,Value=bflow-service \
    --start-time "$(date -u -d '24 hours ago' +%FT%TZ)" --end-time "$(date -u +%FT%TZ)" \
    --period 3600 --statistics Average Maximum \
    --query 'sort_by(Datapoints,&Timestamp)[].[Timestamp,Average,Maximum]' --output table
done

# Bytes ingested into aws/spans per day
aws cloudwatch get-metric-statistics --namespace AWS/Logs --metric-name IncomingBytes \
  --dimensions Name=LogGroupName,Value=aws/spans \
  --start-time "$(date -u -d '7 days ago' +%F)T00:00:00Z" --end-time "$(date -u +%FT%TZ)" \
  --period 86400 --statistics Sum
```

## Gradual rollout and rollback

- Start with a low sample ratio such as `0.1`.
- Increase gradually only if the service remains healthy and the exporter is not returning `401/403/timeout` errors.
- If the app shows startup instability, exporter failures, or elevated CPU/memory pressure, roll back by setting the `OTEL_ENABLED` GitHub variable to `false` and re-running the deploy workflow.
- Keep the DNS changes in mind; ECS replacement has a brief availability gap while the new task comes up.

## Recommended monitoring checks after rollout

```bash
aws ecs describe-services --cluster bflow-cluster --services bflow-service
aws ecs describe-tasks --cluster bflow-cluster --tasks $(aws ecs list-tasks --cluster bflow-cluster --service-name bflow-service --query 'taskArns[]' --output text)
aws logs tail /ecs/bflow --follow | grep -Ei 'otel|opentelemetry|otlp|xray'
aws logs tail aws/spans --follow | head
```

## Notes

- This runbook assumes the user is already operating within the AWS account and has permission to inspect X-Ray, ECS, and CloudWatch logs.
- The CloudWatch `aws/spans` log group is the final place where traces are indexed for search and correlation.
- The actual cost impact is only known after the rollout is complete and the data has been compared to the baseline week.
