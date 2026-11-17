package main

import (
	"testing"

	ecstypes "github.com/aws/aws-sdk-go-v2/service/ecs/types"
)

func TestContainsServiceResource(t *testing.T) {
	tests := []struct {
		name      string
		resources []string
		cluster   string
		service   string
		want      bool
	}{
		{
			name:      "matches service ARN",
			resources: []string{"arn:aws:ecs:us-east-1:123456789012:service/bflow-cluster/bflow-service"},
			cluster:   "bflow-cluster",
			service:   "bflow-service",
			want:      true,
		},
		{
			name:      "rejects another service",
			resources: []string{"arn:aws:ecs:us-east-1:123456789012:service/bflow-cluster/other"},
			cluster:   "bflow-cluster",
			service:   "bflow-service",
			want:      false,
		},
		{
			name:      "matches legacy service ARN",
			resources: []string{"arn:aws:ecs:us-east-1:123456789012:service/bflow-service"},
			cluster:   "bflow-cluster",
			service:   "bflow-service",
			want:      true,
		},
		{
			name:      "rejects another cluster",
			resources: []string{"arn:aws:ecs:us-east-1:123456789012:service/other/bflow-service"},
			cluster:   "bflow-cluster",
			service:   "bflow-service",
			want:      false,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := containsServiceResource(test.resources, test.cluster, test.service); got != test.want {
				t.Fatalf("containsServiceResource() = %v, want %v", got, test.want)
			}
		})
	}
}

func TestIsHealthyRunningTask(t *testing.T) {
	running := "RUNNING"
	stopped := "STOPPED"
	tests := []struct {
		name string
		task *ecstypes.Task
		want bool
	}{
		{
			name: "healthy running task",
			task: &ecstypes.Task{LastStatus: &running, HealthStatus: ecstypes.HealthStatusHealthy},
			want: true,
		},
		{
			name: "unhealthy task",
			task: &ecstypes.Task{LastStatus: &running, HealthStatus: ecstypes.HealthStatusUnhealthy},
		},
		{
			name: "unknown health is not ready",
			task: &ecstypes.Task{LastStatus: &running, HealthStatus: ecstypes.HealthStatusUnknown},
		},
		{
			name: "stopped task",
			task: &ecstypes.Task{LastStatus: &stopped, HealthStatus: ecstypes.HealthStatusHealthy},
		},
		{
			name: "nil task",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := isHealthyRunningTask(test.task); got != test.want {
				t.Fatalf("isHealthyRunningTask() = %v, want %v", got, test.want)
			}
		})
	}
}
