#!/usr/bin/env bash

set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"

echo "=================================================="
echo " BFlow Emergency AWS Cleanup"
echo " Region: $REGION"
echo "=================================================="

confirm() {
  read -r -p "Continue? (yes/no): " answer
  if [[ "$answer" != "yes" ]]; then
    echo "Cancelled."
    exit 0
  fi
}

confirm

echo
echo "=================================================="
echo "1. Removing NAT Gateways"
echo "=================================================="

NATS=$(aws ec2 describe-nat-gateways \
  --region "$REGION" \
  --query "NatGateways[?State!='deleted'].NatGatewayId" \
  --output text)

if [[ -n "$NATS" ]]; then
  for NAT in $NATS; do
    echo "Deleting NAT Gateway: $NAT"

    aws ec2 delete-nat-gateway \
      --region "$REGION" \
      --nat-gateway-id "$NAT"

  done
else
  echo "No NAT gateways found."
fi

echo
echo "=================================================="
echo "2. Releasing unused Elastic IPs"
echo "=================================================="

EIPS=$(aws ec2 describe-addresses \
  --region "$REGION" \
  --query "Addresses[?AssociationId==null].AllocationId" \
  --output text)


if [[ -n "$EIPS" ]]; then

  for EIP in $EIPS; do
    echo "Releasing: $EIP"

    aws ec2 release-address \
      --region "$REGION" \
      --allocation-id "$EIP"

  done

else
  echo "No unused Elastic IPs."
fi

echo
echo "=================================================="
echo "3. Removing ECS services"
echo "=================================================="


CLUSTERS=$(aws ecs list-clusters \
  --region "$REGION" \
  --query "clusterArns[]" \
  --output text)

for CLUSTER in $CLUSTERS; do

  echo "Cluster: $CLUSTER"

  SERVICES=$(aws ecs list-services \
    --region "$REGION" \
    --cluster "$CLUSTER" \
    --query "serviceArns[]" \
    --output text)

  for SERVICE in $SERVICES; do

    echo "Deleting ECS service: $SERVICE"

    aws ecs update-service \
      --region "$REGION" \
      --cluster "$CLUSTER" \
      --service "$SERVICE" \
      --desired-count 0


    aws ecs delete-service \
      --region "$REGION" \
      --cluster "$CLUSTER" \
      --service "$SERVICE" \
      --force

  done

done

echo
echo "=================================================="
echo "4. Removing ECS clusters"
echo "=================================================="

for CLUSTER in $CLUSTERS; do

  echo "Deleting cluster: $CLUSTER"

  aws ecs delete-cluster \
    --region "$REGION" \
    --cluster "$CLUSTER" || true

done

echo
echo "=================================================="
echo "5. Removing RDS instances"
echo "=================================================="

DBS=$(aws rds describe-db-instances \
  --region "$REGION" \
  --query "DBInstances[].DBInstanceIdentifier" \
  --output text)

for DB in $DBS; do

  echo "Deleting RDS: $DB"

  aws rds delete-db-instance \
    --region "$REGION" \
    --db-instance-identifier "$DB" \
    --skip-final-snapshot

done

echo
echo "=================================================="
echo "6. Removing RDS snapshots"
echo "=================================================="

SNAPS=$(aws rds describe-db-snapshots \
  --region "$REGION" \
  --snapshot-type manual \
  --query "DBSnapshots[].DBSnapshotIdentifier" \
  --output text)


for SNAP in $SNAPS; do

  echo "Deleting snapshot: $SNAP"

  aws rds delete-db-snapshot \
    --region "$REGION" \
    --db-snapshot-identifier "$SNAP"

done

echo
echo "=================================================="
echo "7. Removing ECR repositories"
echo "=================================================="

REPOS=$(aws ecr describe-repositories \
  --region "$REGION" \
  --query "repositories[].repositoryName" \
  --output text)


for REPO in $REPOS; do

  echo "Deleting ECR repo: $REPO"

  aws ecr delete-repository \
    --region "$REGION" \
    --repository-name "$REPO" \
    --force

done

echo
echo "=================================================="
echo "8. Removing CloudWatch log groups"
echo "=================================================="

LOGS=$(aws logs describe-log-groups \
  --region "$REGION" \
  --query "logGroups[].logGroupName" \
  --output text)


for LOG in $LOGS; do

  echo "Deleting log group: $LOG"

  aws logs delete-log-group \
    --region "$REGION" \
    --log-group-name "$LOG" || true

done

echo
echo "=================================================="
echo "9. Removing Secrets Manager secrets"
echo "=================================================="

SECRETS=$(aws secretsmanager list-secrets \
  --region "$REGION" \
  --query "SecretList[].Name" \
  --output text)


for SECRET in $SECRETS; do

  echo "Deleting secret: $SECRET"

  aws secretsmanager delete-secret \
    --region "$REGION" \
    --secret-id "$SECRET" \
    --force-delete-without-recovery

done

echo
echo "=================================================="
echo " Cleanup finished"
echo "=================================================="