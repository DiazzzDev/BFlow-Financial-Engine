package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/ec2"
	"github.com/aws/aws-sdk-go-v2/service/ecs"
	ecstypes "github.com/aws/aws-sdk-go-v2/service/ecs/types"
	"github.com/aws/aws-sdk-go-v2/service/secretsmanager"
)

const (
	publicIPMaxAttempts   = 5
	publicIPRetryInterval = 3 * time.Second
	taskLookupMaxAttempts = 5
	taskLookupRetryDelay  = 2 * time.Second
	cloudflareAPIBase     = "https://api.cloudflare.com/client/v4"
)

// ecsServiceActionDetail contains the fields used to validate a steady-state
// service event before querying ECS for its healthy task.
type ecsServiceActionDetail struct {
	EventName  string `json:"eventName"`
	ClusterArn string `json:"clusterArn"`
}

type cloudflareConfig struct {
	APIToken   string `json:"CLOUDFLARE_API_TOKEN"`
	ZoneID     string `json:"CLOUDFLARE_ZONE_ID"`
	RecordID   string `json:"CLOUDFLARE_DNS_RECORD_ID"`
	RecordName string `json:"CLOUDFLARE_DNS_RECORD_NAME"`
}

type cloudflareRecordResponse struct {
	Success bool `json:"success"`
	Result  struct {
		Content string `json:"content"`
	} `json:"result"`
	Errors []struct {
		Message string `json:"message"`
	} `json:"errors"`
}

type syncResult struct {
	Status     string `json:"status"`
	PublicIP   string `json:"publicIp,omitempty"`
	PreviousIP string `json:"previousIp,omitempty"`
}

type handlerDeps struct {
	ecsClient     *ecs.Client
	ec2Client     *ec2.Client
	secretsClient *secretsmanager.Client
	httpClient    *http.Client

	ecsClusterName      string
	ecsServiceName      string
	cloudflareSecretARN string
}

func main() {
	ctx := context.Background()

	cfg, err := config.LoadDefaultConfig(ctx)
	if err != nil {
		panic(fmt.Sprintf("unable to load AWS config: %v", err))
	}

	deps := &handlerDeps{
		ecsClient:           ecs.NewFromConfig(cfg),
		ec2Client:           ec2.NewFromConfig(cfg),
		secretsClient:       secretsmanager.NewFromConfig(cfg),
		httpClient:          &http.Client{Timeout: 10 * time.Second},
		ecsClusterName:      os.Getenv("ECS_CLUSTER_NAME"),
		ecsServiceName:      os.Getenv("ECS_SERVICE_NAME"),
		cloudflareSecretARN: os.Getenv("CLOUDFLARE_SECRET_ARN"),
	}

	lambda.Start(deps.handle)
}

func (d *handlerDeps) handle(ctx context.Context, event events.CloudWatchEvent) (syncResult, error) {
	if event.Source != "aws.ecs" || event.DetailType != "ECS Service Action" {
		return syncResult{Status: "skipped: unexpected event type"}, nil
	}

	var detail ecsServiceActionDetail
	if err := json.Unmarshal(event.Detail, &detail); err != nil {
		return syncResult{}, fmt.Errorf("decoding event detail: %w", err)
	}

	if detail.EventName != "SERVICE_STEADY_STATE" {
		fmt.Printf("Ignoring event: eventName=%s\n", detail.EventName)
		return syncResult{Status: "skipped: service not steady"}, nil
	}

	if !strings.HasSuffix(detail.ClusterArn, "/"+d.ecsClusterName) {
		fmt.Printf("Ignoring event: clusterArn=%s\n", detail.ClusterArn)
		return syncResult{Status: "skipped: different service"}, nil
	}
	if !containsServiceResource(event.Resources, d.ecsClusterName, d.ecsServiceName) {
		fmt.Printf("Ignoring event resources: %v\n", event.Resources)
		return syncResult{Status: "skipped: different service"}, nil
	}

	eniID, err := d.currentTaskENI(ctx)
	if err != nil {
		return syncResult{}, err
	}
	if eniID == "" {
		fmt.Println("No RUNNING task with an ENI attachment found for the service.")
		return syncResult{Status: "skipped: no active task"}, nil
	}

	publicIP, err := d.getPublicIPWithRetry(ctx, eniID)
	if err != nil {
		return syncResult{}, err
	}
	if publicIP == "" {
		return syncResult{}, fmt.Errorf("no public IP resolved for ENI %s after %d attempts", eniID, publicIPMaxAttempts)
	}

	cfConfig, err := d.getCloudflareConfig(ctx)
	if err != nil {
		return syncResult{}, err
	}

	currentIP, err := d.getCurrentDNSIP(cfConfig)
	if err != nil {
		return syncResult{}, err
	}

	if currentIP == publicIP {
		fmt.Printf("DNS already points to %s. Nothing to do.\n", publicIP)
		return syncResult{Status: "noop", PublicIP: publicIP}, nil
	}

	if err := d.updateDNSRecord(cfConfig, publicIP); err != nil {
		return syncResult{}, err
	}

	fmt.Printf("Updated %s from %s to %s.\n", cfConfig.RecordName, currentIP, publicIP)
	return syncResult{Status: "updated", PublicIP: publicIP, PreviousIP: currentIP}, nil
}

func containsServiceResource(resources []string, clusterName, serviceName string) bool {
	resourceSuffix := clusterName + "/" + serviceName
	for _, resource := range resources {
		if strings.HasSuffix(resource, ":service/"+resourceSuffix) ||
			strings.HasSuffix(resource, "/service/"+resourceSuffix) ||
			strings.HasSuffix(resource, ":service/"+serviceName) ||
			strings.HasSuffix(resource, "/service/"+serviceName) {
			return true
		}
	}
	return false
}

func isHealthyRunningTask(task *ecstypes.Task) bool {
	return task != nil && task.LastStatus != nil && *task.LastStatus == "RUNNING" &&
		task.HealthStatus == ecstypes.HealthStatusHealthy
}

// currentTaskENI resolves the ENI of the most recently started RUNNING
// task for the service, retrying on empty results.
//
// The retry here is defensive, not a fix for a confirmed issue: an
// EventBridge invocation could in principle land inside a brief
// control-plane propagation gap right after the task flips to
// RUNNING. In practice, the actual bug that caused this to fail
// consistently was downstream, in lookupNewestTaskENI's attachment
// matching — see the comment there.
func (d *handlerDeps) currentTaskENI(ctx context.Context) (string, error) {
	for attempt := 1; attempt <= taskLookupMaxAttempts; attempt++ {
		eniID, err := d.lookupNewestTaskENI(ctx)
		if err != nil {
			return "", err
		}
		if eniID != "" {
			return eniID, nil
		}

		fmt.Printf(
			"No RUNNING task found yet for %s (attempt %d/%d).\n",
			d.ecsServiceName, attempt, taskLookupMaxAttempts,
		)
		if attempt < taskLookupMaxAttempts {
			time.Sleep(taskLookupRetryDelay)
		}
	}

	return "", nil
}

// lookupNewestTaskENI queries ECS directly for healthy RUNNING tasks of
// the service and returns the ENI of the most recently started one.
//
// This deliberately ignores the ENI carried in the triggering event.
// During a rolling deployment (maximumPercent > 100), the old task
// and the new task are both RUNNING at once, each firing its own
// "ECS Task State Change" event — and EventBridge does not guarantee
// delivery order. Trusting whichever event arrives last can revert
// the DNS record to a task that's about to be stopped. Querying live
// state and picking the newest by StartedAt makes this convergent
// regardless of event ordering.
func (d *handlerDeps) lookupNewestTaskENI(ctx context.Context) (string, error) {
	listOutput, err := d.ecsClient.ListTasks(ctx, &ecs.ListTasksInput{
		Cluster:       &d.ecsClusterName,
		ServiceName:   &d.ecsServiceName,
		DesiredStatus: ecstypes.DesiredStatusRunning,
	})
	if err != nil {
		return "", fmt.Errorf("listing ECS tasks: %w", err)
	}
	if len(listOutput.TaskArns) == 0 {
		return "", nil
	}

	describeOutput, err := d.ecsClient.DescribeTasks(ctx, &ecs.DescribeTasksInput{
		Cluster: &d.ecsClusterName,
		Tasks:   listOutput.TaskArns,
	})
	if err != nil {
		return "", fmt.Errorf("describing ECS tasks: %w", err)
	}

	var newest *ecstypes.Task
	for i := range describeOutput.Tasks {
		task := &describeOutput.Tasks[i]
		if !isHealthyRunningTask(task) {
			continue
		}
		if newest == nil || taskTimestamp(task).After(taskTimestamp(newest)) {
			newest = task
		}
	}
	if newest == nil {
		return "", nil
	}

	// Don't filter by attachment.Type here: the string ECS uses for an
	// ENI attachment differs between the raw EventBridge event payload
	// ("eni") and this DescribeTasks API response
	// ("ElasticNetworkInterface"). Matching on the wrong one silently
	// skips every attachment with no error — which is exactly what
	// happened before this comment existed. The "networkInterfaceId"
	// detail key is stable across both, so key off that instead.
	for _, attachment := range newest.Attachments {
		for _, item := range attachment.Details {
			if item.Name != nil && *item.Name == "networkInterfaceId" && item.Value != nil {
				return *item.Value, nil
			}
		}
	}

	return "", nil
}

func taskTimestamp(task *ecstypes.Task) time.Time {
	if task.StartedAt != nil {
		return *task.StartedAt
	}
	if task.CreatedAt != nil {
		return *task.CreatedAt
	}
	return time.Time{}
}

func (d *handlerDeps) getPublicIPWithRetry(ctx context.Context, eniID string) (string, error) {
	for attempt := 1; attempt <= publicIPMaxAttempts; attempt++ {
		output, err := d.ec2Client.DescribeNetworkInterfaces(ctx, &ec2.DescribeNetworkInterfacesInput{
			NetworkInterfaceIds: []string{eniID},
		})
		if err != nil {
			return "", fmt.Errorf("describing network interface %s: %w", eniID, err)
		}

		if len(output.NetworkInterfaces) > 0 {
			association := output.NetworkInterfaces[0].Association
			if association != nil && association.PublicIp != nil && *association.PublicIp != "" {
				return *association.PublicIp, nil
			}
		}

		fmt.Printf("Public IP not ready yet for %s (attempt %d/%d).\n", eniID, attempt, publicIPMaxAttempts)
		if attempt < publicIPMaxAttempts {
			time.Sleep(publicIPRetryInterval)
		}
	}

	return "", nil
}

func (d *handlerDeps) getCloudflareConfig(ctx context.Context) (*cloudflareConfig, error) {
	output, err := d.secretsClient.GetSecretValue(ctx, &secretsmanager.GetSecretValueInput{
		SecretId: &d.cloudflareSecretARN,
	})
	if err != nil {
		return nil, fmt.Errorf("reading Cloudflare secret: %w", err)
	}

	var cfg cloudflareConfig
	if err := json.Unmarshal([]byte(*output.SecretString), &cfg); err != nil {
		return nil, fmt.Errorf("decoding Cloudflare secret: %w", err)
	}

	return &cfg, nil
}

func (d *handlerDeps) cloudflareRequest(method, path string, cfg *cloudflareConfig, payload any) (*cloudflareRecordResponse, error) {
	var body io.Reader
	if payload != nil {
		encoded, err := json.Marshal(payload)
		if err != nil {
			return nil, fmt.Errorf("encoding Cloudflare payload: %w", err)
		}
		body = bytes.NewReader(encoded)
	}

	req, err := http.NewRequest(method, cloudflareAPIBase+path, body)
	if err != nil {
		return nil, fmt.Errorf("building Cloudflare request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+cfg.APIToken)
	req.Header.Set("Content-Type", "application/json")

	resp, err := d.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling Cloudflare API: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("reading Cloudflare response: %w", err)
	}

	var parsed cloudflareRecordResponse
	if err := json.Unmarshal(respBody, &parsed); err != nil {
		return nil, fmt.Errorf("decoding Cloudflare response (status %d): %w", resp.StatusCode, err)
	}

	return &parsed, nil
}

func (d *handlerDeps) getCurrentDNSIP(cfg *cloudflareConfig) (string, error) {
	path := fmt.Sprintf("/zones/%s/dns_records/%s", cfg.ZoneID, cfg.RecordID)

	result, err := d.cloudflareRequest(http.MethodGet, path, cfg, nil)
	if err != nil {
		return "", err
	}
	if !result.Success {
		return "", fmt.Errorf("failed to read DNS record: %v", result.Errors)
	}

	return result.Result.Content, nil
}

func (d *handlerDeps) updateDNSRecord(cfg *cloudflareConfig, publicIP string) error {
	path := fmt.Sprintf("/zones/%s/dns_records/%s", cfg.ZoneID, cfg.RecordID)

	payload := map[string]any{
		"type":    "A",
		"name":    cfg.RecordName,
		"content": publicIP,
		"proxied": true,
		"ttl":     1,
	}

	result, err := d.cloudflareRequest(http.MethodPut, path, cfg, payload)
	if err != nil {
		return err
	}
	if !result.Success {
		return fmt.Errorf("failed to update DNS record: %v", result.Errors)
	}

	return nil
}
