package io.bimurto.crac.messaging

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapSetter
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest


@Component
class SNSNotificationSender(
    private val snsClient: SnsClient,
    private val objectMapper: ObjectMapper,
    private val openTelemetry: OpenTelemetry
) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(SNSNotificationSender::class.java)
    }

    fun send(snsDto: SNSDto) {
        val messageAttributes = mutableMapOf<String, MessageAttributeValue>()
        val sqsAttributesSetter = TextMapSetter<MutableMap<String, MessageAttributeValue>> { carrier, key, value ->
            carrier?.set(
                key, MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(value)
                    .build()
            )
        }

        // Inject trace context into message attributes
        openTelemetry.propagators.textMapPropagator
            .inject(Context.current(), messageAttributes, sqsAttributesSetter)

        try {
            val message = objectMapper.writeValueAsString(snsDto.data)
            snsClient.publish(
                PublishRequest.builder()
                    .topicArn(snsDto.snsTopic)
                    .message(message)
                    .messageAttributes(messageAttributes)
                    .build()
            )
        } catch (e: SdkClientException) {
            log.error(
                "{} {}",
                StructuredArguments.kv("method", "send"),
                StructuredArguments.kv("message", "Sns sending caught AmazonSdkClientException"), e
            )
            throw e
        } catch (e: JsonProcessingException) {
            throw RuntimeException(e)
        }
    }
}

class SNSDto(
    val data: Any?,
    val snsTopic: String
)