package io.bimurto.crac.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapSetter
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.MessagingException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

@Service
class SQSNotificationSender(
    val sqsAsyncClient: SqsAsyncClient,
    val objectMapper: ObjectMapper,
    val openTelemetry: OpenTelemetry,
) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(SQSNotificationSender::class.java)
    }

    private val sqsAttributesSetter = TextMapSetter<MutableMap<String, MessageAttributeValue>> { carrier, key, value ->
        carrier?.set(key, MessageAttributeValue.builder()
            .dataType("String")
            .stringValue(value)
            .build()
        )
    }

    @Retryable(
        value = [MessagingException::class],
        maxAttemptsExpression = "\${sns.retry.max_attempts}",
        backoff = Backoff(
            delayExpression = "\${sns.retry.backoff_delay}",
            maxDelayExpression = "\${sns.retry.backoff_max_delay}",
            multiplierExpression = "\${sns.retry.backoff_multiplier}"
        )
    )
    fun send(queue: String, event: Any) {
        try {
            val messageAttributes = mutableMapOf<String, MessageAttributeValue>()
            
            // Inject trace context into message attributes
            openTelemetry.propagators.textMapPropagator
                .inject(Context.current(), messageAttributes, sqsAttributesSetter)
                
            sqsAsyncClient.sendMessage(
                SendMessageRequest.builder()
                    .queueUrl(queue)
                    .messageBody(objectMapper.writeValueAsString(event))
                    .messageAttributes(messageAttributes)
                    .build()
            )
        } catch (ex: MessagingException) {
            log.error(
                "{} {}",
                kv("event", "send_sqs_message"),
                kv("message", "Failed to send sqs message!"), ex
            )
            throw ex
        }
    }
}