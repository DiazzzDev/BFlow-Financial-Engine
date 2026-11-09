package bflow.gmail.messaging;

import bflow.gmail.service.GmailImportProcessor;
import bflow.gmail.service.GmailImportQueuePublisher;
import bflow.receipts.messaging.AbstractSqsPollingWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

@Component
@ConditionalOnExpression("! '${aws.sqs.gmail-import-queue-url:}'.isEmpty()")
public class GmailImportQueueListener extends AbstractSqsPollingWorker {
    private static final int WAIT_SECONDS = 10;
    private final ObjectMapper mapper;
    private final GmailImportProcessor processor;

    public GmailImportQueueListener(
            final SqsClient client, final ObjectMapper objectMapper,
            final GmailImportProcessor importProcessor,
            @Value("${aws.sqs.gmail-import-queue-url}") final String queueUrl
    ) {
        super(client, queueUrl, WAIT_SECONDS, 5, 5);
        this.mapper = objectMapper;
        this.processor = importProcessor;
    }

    @Override protected String workerName() { return "gmail-import-listener"; }

    @Override
    protected boolean handle(final Message message) {
        try {
            processor.process(mapper.readValue(message.body(),
                    GmailImportQueuePublisher.ImportJob.class));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
