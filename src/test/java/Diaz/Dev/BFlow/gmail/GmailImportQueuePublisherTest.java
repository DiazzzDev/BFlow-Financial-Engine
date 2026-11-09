package Diaz.Dev.BFlow.gmail;

import bflow.gmail.service.GmailImportQueuePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GmailImportQueuePublisherTest {
    @Test
    void serializesAndPublishesImportJobToConfiguredQueue() {
        SqsClient sqs = mock(SqsClient.class);
        GmailImportQueuePublisher publisher = new GmailImportQueuePublisher(sqs, new ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(publisher, "queueUrl", "https://sqs.test/queue");
        var job = new GmailImportQueuePublisher.ImportJob(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "message-id", "attachment-id", "application/pdf");

        publisher.publish(job);

        var captor = org.mockito.ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(captor.capture());
        assertThat(captor.getValue().queueUrl()).isEqualTo("https://sqs.test/queue");
        assertThat(captor.getValue().messageBody()).contains("message-id", "attachment-id", "application/pdf");
    }

    @Test
    void failsClearlyWhenQueueIsNotConfigured() {
        GmailImportQueuePublisher publisher = new GmailImportQueuePublisher(mock(SqsClient.class), new ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(publisher, "queueUrl", " ");

        assertThatThrownBy(() -> publisher.publish(new GmailImportQueuePublisher.ImportJob(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "message-id", "attachment-id", "application/pdf")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void wrapsSerializationAndSqsFailuresWithoutLeakingPayload() throws Exception {
        SqsClient sqs = mock(SqsClient.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new IllegalArgumentException("serialization failure"));
        GmailImportQueuePublisher publisher = new GmailImportQueuePublisher(sqs, mapper);
        org.springframework.test.util.ReflectionTestUtils.setField(publisher, "queueUrl", "https://sqs.test/queue");

        assertThatThrownBy(() -> publisher.publish(new GmailImportQueuePublisher.ImportJob(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "private-message-id", "private-attachment-id", "application/pdf")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to enqueue Gmail import")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
