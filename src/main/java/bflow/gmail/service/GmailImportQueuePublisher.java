package bflow.gmail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GmailImportQueuePublisher {
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    @Value("${aws.sqs.gmail-import-queue-url:}") private String queueUrl;

    public void publish(final ImportJob job) {
        if (queueUrl.isBlank()) throw new IllegalStateException("Gmail import queue is not configured");
        try {
            sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(job)).build());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to enqueue Gmail import", ex);
        }
    }

    public record ImportJob(UUID importId, UUID userId, UUID walletId,
                            UUID integrationId, String messageId,
                            String attachmentId, String mimeType) { }
}
