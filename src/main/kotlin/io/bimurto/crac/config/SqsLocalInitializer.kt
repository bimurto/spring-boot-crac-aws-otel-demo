package io.bimurto.crac.config

import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName

@Component
class SqsLocalInitializer(
    val s3Client: S3Client,
    val snsClient: SnsClient,
    val sqsClient: SqsAsyncClient,
    val environment: Environment,
) {

    val log: Logger = LoggerFactory.getLogger(SqsLocalInitializer::class.java)

    @PostConstruct
    fun postConstruct() {
        if (isLocalOrTest() && !isCracEnabled()) {
            val s3Bucket = "demo-bucket"
            val snsTopic = "demo-topic"
            val sqsQueue = "demo-queue"
            try {
                s3Client.createBucket { it.bucket(s3Bucket) }
            }catch (ex: Exception){
                //ignore if bucket already exists
                log.error("Bucket already exists {}", ex.message)
            }
            val topicArn = snsClient.createTopic { it.name(snsTopic) }.topicArn()
            val queueUrl = sqsClient.createQueue(CreateQueueRequest.builder().queueName(sqsQueue).build()).get().queueUrl()

            val queueArn = sqsClient.getQueueAttributes(
                GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build()
            ).get().attributes()[QueueAttributeName.QUEUE_ARN]

            snsClient.subscribe {
                it.topicArn(topicArn)
                it.protocol("sqs")
                it.endpoint(queueArn)
            }
            log.info("Subscribed {} to SNS topic {}", sqsQueue, snsTopic)
        }

    }

    private fun isLocalOrTest() = environment.activeProfiles.contains("local") || environment.activeProfiles.contains("test")

    private fun isCracEnabled() = environment.activeProfiles.contains("crac")
}
