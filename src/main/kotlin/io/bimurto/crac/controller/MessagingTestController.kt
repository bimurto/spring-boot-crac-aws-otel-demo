package io.bimurto.crac.controller

import io.bimurto.crac.messaging.SNSDto
import io.bimurto.crac.messaging.SNSNotificationSender
import io.bimurto.crac.messaging.SQSNotificationSender
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/test/messaging")
class MessagingTestController(
    private val snsNotificationSender: SNSNotificationSender,
    private val sqsNotificationSender: SQSNotificationSender,
    @Value("\${topic.demo}") private val demoTopicArn: String,
    @Value("\${queue.demo}") private val demoQueueUrl: String,
) {

    @PostMapping("/sns")
    fun publishToSns(@RequestBody request: MessageRequest) {
        snsNotificationSender.send(SNSDto(data = request, snsTopic = demoTopicArn))
    }

    @PostMapping("/sqs")
    fun publishToSqs(@RequestBody request: MessageRequest) {
        sqsNotificationSender.send(queue = demoQueueUrl, event = request)
    }
}

data class MessageRequest(val name: String)
