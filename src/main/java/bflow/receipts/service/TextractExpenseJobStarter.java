package bflow.receipts.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.DocumentLocation;
import software.amazon.awssdk.services.textract.model.NotificationChannel;
import software.amazon.awssdk.services.textract.model.S3Object;
import software.amazon.awssdk.services.textract.model.StartExpenseAnalysisRequest;

/** Starts the shared asynchronous Textract expense-analysis workflow for a private S3 object. */
@Service
@RequiredArgsConstructor
public class TextractExpenseJobStarter {
    private final TextractClient textractClient;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.sns.receipt-ocr-results-topic-arn}")
    private String resultsTopicArn;

    @Value("${aws.textract.sns-role-arn}")
    private String textractSnsRoleArn;

    public String start(final String objectKey, final String clientRequestToken) {
        return textractClient.startExpenseAnalysis(StartExpenseAnalysisRequest.builder()
                .documentLocation(DocumentLocation.builder()
                        .s3Object(S3Object.builder().bucket(bucket).name(objectKey).build()).build())
                .notificationChannel(NotificationChannel.builder()
                        .snsTopicArn(resultsTopicArn).roleArn(textractSnsRoleArn).build())
                .clientRequestToken(clientRequestToken)
                .build()).jobId();
    }
}
