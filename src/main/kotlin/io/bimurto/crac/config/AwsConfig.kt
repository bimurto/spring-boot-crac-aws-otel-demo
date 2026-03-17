package io.bimurto.crac.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import software.amazon.awssdk.regions.providers.AwsRegionProvider
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.net.URI


@Configuration
class AwsConfig(
    private val regionProvider: AwsRegionProvider,
    @Value("\${cloud.aws.sqs.client.endpoint:}") private val sqsEndpoint: String,
    @Value("\${cloud.aws.sns.client.endpoint:}") private val snsEndpoint: String,
) {

    @Bean
    fun s3Client(awsTracingInterceptor: ExecutionInterceptor): S3Client {
        return S3Client.builder()
            .overrideConfiguration { builder ->
                builder.addExecutionInterceptor(awsTracingInterceptor)
            }
            .build()
    }

    @Bean
    fun amazonSQSClient(awsTracingInterceptor: ExecutionInterceptor): SqsAsyncClient {
        val region = regionProvider.region

        val overrideConfig = ClientOverrideConfiguration.builder()
            .build()

        val sqsBuilder = SqsAsyncClient.builder()
            .overrideConfiguration(overrideConfig)
            .region(region)

        if (sqsEndpoint.isNotEmpty()) {
            sqsBuilder.endpointOverride(URI.create(sqsEndpoint))
        }

        val sqsAsyncClient = sqsBuilder
            .overrideConfiguration { builder ->
                builder.addExecutionInterceptor(awsTracingInterceptor)
            }.build()

        return sqsAsyncClient
    }

    @Primary
    @Bean
    fun amazonSNS(awsTracingInterceptor: ExecutionInterceptor): SnsClient {
        val region = regionProvider.region
        val snsClientBuilder = SnsClient.builder()
        snsClientBuilder.region(region)
        if (snsEndpoint.isNotEmpty()) {
            snsClientBuilder.endpointOverride(URI.create(snsEndpoint))
        }
        return snsClientBuilder
            .overrideConfiguration { builder ->
                builder.addExecutionInterceptor(awsTracingInterceptor)
            }.build()
    }
}

