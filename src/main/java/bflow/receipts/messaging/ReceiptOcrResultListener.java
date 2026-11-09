package bflow.receipts.messaging;

import bflow.receipts.service.ReceiptOcrResultProcessor;
import bflow.gmail.service.GmailOcrResultProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Polls the receipt OCR results queue for Textract job-completion
 * notifications, delivered through SNS. Each SQS message body is the
 * SNS envelope ({@link SnsEnvelope}); its {@code Message} field is
 * itself JSON, the Textract notification ({@link
 * TextractCompletionNotification}).
 */
@Slf4j
@Component
public final class ReceiptOcrResultListener
        extends AbstractSqsPollingWorker {

    /** Service that fetches and maps the completed job's result. */
    private final ReceiptOcrResultProcessor receiptOcrResultProcessor;
    private final GmailOcrResultProcessor gmailOcrResultProcessor;

    /** Used to parse the SNS envelope and the nested notification. */
    private final ObjectMapper objectMapper;

    /**
     * Creates the listener and wires it to the results queue.
     *
     * @param sqsClient the SQS client to poll with
     * @param resultProcessor service that processes a completed job
     * @param mapper used to parse the SNS/Textract JSON
     * @param queueUrl URL of the OCR results queue
     * @param waitTimeSeconds long-poll wait time, in seconds
     * @param maxMessages maximum messages per receive call
     * @param errorBackoffSeconds pause after a poll failure, in
     *         seconds
     */
    @Autowired
    public ReceiptOcrResultListener(
            final SqsClient sqsClient,
            final ReceiptOcrResultProcessor resultProcessor,
            final GmailOcrResultProcessor gmailResultProcessor,
            final ObjectMapper mapper,
            @Value("${aws.sqs.receipt-ocr-results-queue-url}")
            final String queueUrl,
            @Value("${app.ocr.result-poll-wait-seconds}")
            final int waitTimeSeconds,
            @Value("${app.ocr.poll-max-messages}")
            final int maxMessages,
            @Value("${app.ocr.poll-error-backoff-seconds}")
            final int errorBackoffSeconds
    ) {
        super(sqsClient, queueUrl, waitTimeSeconds, maxMessages,
                errorBackoffSeconds);
        this.receiptOcrResultProcessor = resultProcessor;
        this.gmailOcrResultProcessor = gmailResultProcessor;
        this.objectMapper = mapper;
    }

    /** Compatibility constructor for isolated receipt tests without the Gmail module. */
    public ReceiptOcrResultListener(
            final SqsClient sqsClient,
            final ReceiptOcrResultProcessor resultProcessor,
            final ObjectMapper mapper,
            final String queueUrl,
            final int waitTimeSeconds,
            final int maxMessages,
            final int errorBackoffSeconds
    ) {
        this(sqsClient, resultProcessor, null, mapper, queueUrl,
                waitTimeSeconds, maxMessages, errorBackoffSeconds);
    }

    @Override
    protected String workerName() {
        return "receipt-ocr-result-listener";
    }

    @Override
    protected boolean handle(final Message message) {
        SnsEnvelope envelope;
        try {
            envelope = objectMapper.readValue(
                    message.body(), SnsEnvelope.class);
        } catch (Exception ex) {
            log.error(
                    "Malformed SNS envelope in OCR result message {}",
                    message.messageId(), ex
            );
            return true; // not recoverable by retrying — drop it
        }

        if (!SnsEnvelope.TYPE_NOTIFICATION.equals(envelope.type())
                || !StringUtils.hasText(envelope.message())) {
            log.warn(
                    "Ignoring unexpected SNS message type '{}' on OCR "
                            + "results queue (message {})",
                    envelope.type(), message.messageId()
            );
            return true;
        }

        TextractCompletionNotification notification;
        try {
            notification = objectMapper.readValue(
                    envelope.message(),
                    TextractCompletionNotification.class
            );
        } catch (Exception ex) {
            log.error(
                    "Malformed Textract notification in message {}",
                    message.messageId(), ex
            );
            return true; // not recoverable by retrying — drop it
        }

        if (!StringUtils.hasText(notification.jobId())) {
            log.error(
                    "Textract notification in message {} is missing "
                            + "a JobId",
                    message.messageId()
            );
            return true;
        }

        if (!receiptOcrResultProcessor.processCompletion(notification.jobId(), notification.status())
                && gmailOcrResultProcessor != null) {
            gmailOcrResultProcessor.processCompletion(notification.jobId(), notification.status());
        }
        return true;
    }
}
