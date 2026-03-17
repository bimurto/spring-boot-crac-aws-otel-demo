package io.bimurto.crac.listener


import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.OpenTelemetry
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.model.Message


@Component
class SqsListener(
    objectMapper: ObjectMapper,
    openTelemetry: OpenTelemetry,
) : AbstractListener(objectMapper = objectMapper, openTelemetry = openTelemetry, isSnsMessage = false) {

    @SqsListener(value = ["demo-queue"])
    fun listen(message: Message) {
        handle<SqsDto>(message, block = {
            println(it)
        })
    }

    override fun getEventName() = "demo-queue"
}

data class SqsDto(
    val name: String
)
