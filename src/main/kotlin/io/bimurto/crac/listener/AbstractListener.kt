package io.bimurto.crac.listener

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import java.time.OffsetDateTime

abstract class AbstractListener(
    val objectMapper: ObjectMapper,
    val openTelemetry: OpenTelemetry,
    val isSnsMessage: Boolean = true,
) {

    val log: Logger = LoggerFactory.getLogger(AbstractListener::class.java)


    inline fun <reified T> handle(
        message: Message,
        block: (T) -> Unit
    ) {
        val extractedContext = try {
            if (isSnsMessage) {
                @Suppress("UNCHECKED_CAST")
                val snsEnvelope = objectMapper.readValue(message.body(), Map::class.java) as Map<String, Any>
                openTelemetry.propagators.textMapPropagator
                    .extract(Context.current(), snsEnvelope, SnsAttributesGetter)
            } else {
                openTelemetry.propagators.textMapPropagator
                    .extract(Context.current(), message, SqsAttributesGetter)
            }
        } catch (_: Exception) {
            Context.current()
        }

        val span = openTelemetry.getTracer("sqs-listener")
            .spanBuilder("SQS.ReceiveMessage")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute("rpc.service", "sqs")
            .setAttribute("messaging.destination_name", getEventName())
            .setAttribute("rpc.method", "ReceiveMessage")
            .setParent(extractedContext)
            .startSpan()

        try {
            extractedContext.with(span).makeCurrent().use {
                message.body()
                    .also { logMessage(it) }
                    .let {
                        if (isSnsMessage) {
                            objectMapper.readValue(it, SnsNotification::class.java)
                                .let { snsEnvelope ->
                                    objectMapper.readValue(snsEnvelope.message, T::class.java)
                                }
                        } else {
                            objectMapper.readValue(it, T::class.java)
                        }
                    }
                    .run { block(this) }

                span.setStatus(StatusCode.OK)
            }
        } catch (e: Exception) {
            logErrorMessage(message, e)
            span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
            span.recordException(e)
            throw e
        } finally {
            span.end()
        }
    }

    fun logMessage(data: String) {
        log.info(
            "{} {} {} {} {}",
            kv("method", "handle"),
            kv("message", "SQS message received: $data"),
            kv("event", getEventName()),
            kv("category", "received"),
            kv("payload", data)
        )
    }

    fun logErrorMessage(message: Message, e: Exception) {
        val attempt =
            message.attributes().getOrDefault(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "0").toInt()
        log.error(
            "{} {} {} {} {} {}",
            kv("method", "handle"),
            kv("message", "SQS message error"),
            kv("event", getEventName()),
            kv("category", "consumption_failed"),
            kv("payload", message.body()),
            kv("attempt", attempt),
            e
        )
    }

    abstract fun getEventName(): String

    companion object {
        object SqsAttributesGetter : TextMapGetter<Message> {
            override fun keys(carrier: Message): Iterable<String> {
                return carrier.messageAttributes().keys
            }

            override fun get(carrier: Message?, key: String): String? {
                return carrier?.messageAttributes()?.get(key)?.stringValue()
            }
        }

        object SnsAttributesGetter : TextMapGetter<Map<String, Any>> {
            override fun keys(carrier: Map<String, Any>): Iterable<String> {
                @Suppress("UNCHECKED_CAST")
                val messageAttributes = carrier["MessageAttributes"] as? Map<String, Map<String, String>>
                return messageAttributes?.keys ?: emptySet()
            }

            override fun get(carrier: Map<String, Any>?, key: String): String? {
                @Suppress("UNCHECKED_CAST")
                val messageAttributes = carrier?.get("MessageAttributes") as? Map<String, Map<String, String>>
                return messageAttributes?.get(key)?.get("Value")
            }
        }
    }
}

data class SnsNotification(
    @get:JsonProperty("Message")
    val message: String,
    @get:JsonProperty("Timestamp")
    val timestamp: OffsetDateTime
)