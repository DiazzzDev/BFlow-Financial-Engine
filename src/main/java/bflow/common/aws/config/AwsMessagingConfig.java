package bflow.common.aws.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.rekognition.RekognitionClient;

/**
 * AWS configuration for the messaging clients used by the async
 * receipt OCR pipeline: SQS (request/result queues), SNS (Textract
 * completion notifications) and Textract itself.
 *
 * <p>Same convention as {@link AwsS3Config}: the AWS default
 * credential provider chain is used, no static credentials are
 * configured here.</p>
 */
@Configuration
public class AwsMessagingConfig {

    /** AWS region for every client configured here. */
    @Value("${aws.region}")
    private String region;

    /**
     * Create and configure the SQS client bean, shared by the
     * receipt OCR request publisher and both polling listeners.
     *
     * @return configured SqsClient instance
     */
    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(region))
                .build();
    }

    /**
     * Create and configure the SNS client bean. Not used directly
     * to publish (Textract publishes completion notifications
     * itself, via {@code NotificationChannel}); kept for symmetry
     * and any future direct-publish need.
     *
     * @return configured SnsClient instance
     */
    @Bean
    public SnsClient snsClient() {
        return SnsClient.builder()
                .region(Region.of(region))
                .build();
    }

    /**
     * Create and configure the Textract client bean used to submit
     * {@code StartExpenseAnalysis} jobs and to fetch their results
     * with {@code GetExpenseAnalysis}.
     *
     * @return configured TextractClient instance
     */
    @Bean
    public TextractClient textractClient() {
        return TextractClient.builder()
                .region(Region.of(region))
                .build();
    }

    /** Creates the image moderation client used before user media is served. */
    @Bean
    public RekognitionClient rekognitionClient() {
        return RekognitionClient.builder()
                .region(Region.of(region))
                .build();
    }
}
