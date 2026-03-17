package io.bimurto.crac.config

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.trace.samplers.SamplingResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.StringUtils
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import java.util.function.BiFunction

@Configuration
class OpenTelemetryConfig {

    @Bean
    fun awsSdkTelemetry(openTelemetry: OpenTelemetry): AwsSdkTelemetry {
        return AwsSdkTelemetry.builder(openTelemetry)
            .setCaptureExperimentalSpanAttributes(true)
            .setRecordIndividualHttpError(true)
            .build()
    }

    @Bean
    fun awsTracingInterceptor(awsSdkTelemetry: AwsSdkTelemetry): ExecutionInterceptor {
        return awsSdkTelemetry.newExecutionInterceptor()
    }

    @Bean
    @ConditionalOnProperty(value = ["crac.enabled"], havingValue = "true")
    fun openTelemetryCustomizer(): AutoConfigurationCustomizerProvider {
        return AutoConfigurationCustomizerProvider { p: AutoConfigurationCustomizer? ->
            p!!.addSamplerCustomizer { fallback: Sampler?, _: ConfigProperties? ->
                UrlPathSampler(fallback)
            }
            p.addSpanExporterCustomizer(
                BiFunction { base: SpanExporter, _: ConfigProperties? ->
                    CracSpanExporter(base)
                }
            )
            p.addMetricExporterCustomizer(
                BiFunction { base: MetricExporter, _: ConfigProperties? ->
                    CracMetricExporter(base)
                }
            )
            p.addLogRecordExporterCustomizer(
                BiFunction { base: LogRecordExporter, _: ConfigProperties? ->
                    CracLogRecordExporter(base)
                }
            )
        }
    }
}

class UrlPathSampler(
    private val fallback: Sampler?
) : Sampler {
    override fun shouldSample(
        parentContext: Context,
        traceId: String,
        name: String,
        spanKind: SpanKind,
        attributes: Attributes,
        parentLinks: List<LinkData?>
    ): SamplingResult? {
        val attributeValue = attributes.get(AttributeKey.stringKey("url.path"))
        val dropSpansEnv = System.getenv("OTEL_DROP_SPANS")
        if (StringUtils.hasText(attributeValue)
            && StringUtils.hasText(dropSpansEnv)
            && dropSpansEnv.split(",").contains(attributeValue)
        ) {
            return SamplingResult.drop()
        }
        return fallback?.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks)
    }

    override fun getDescription(): String = "Drop spans based on urls in OTEL_DROP_SPANS property"
}

class CracSpanExporter(val base: SpanExporter) : SpanExporter {
    override fun export(spans: Collection<SpanData?>): CompletableResultCode {
        return if (!CracConfig.isRestored)
            CompletableResultCode.ofSuccess()
        else base.export(spans)
    }

    override fun flush(): CompletableResultCode = base.flush()
    override fun shutdown(): CompletableResultCode = base.shutdown()
}

class CracMetricExporter(val base: MetricExporter) : MetricExporter {
    override fun export(metrics: Collection<MetricData?>): CompletableResultCode {
        return if (!CracConfig.isRestored)
            CompletableResultCode.ofSuccess()
        else base.export(metrics)
    }

    override fun flush(): CompletableResultCode = base.flush()
    override fun shutdown(): CompletableResultCode = base.shutdown()
    override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality =
        base.getAggregationTemporality(instrumentType)

}

class CracLogRecordExporter(val base: LogRecordExporter) : LogRecordExporter {
    override fun export(logRecords: Collection<LogRecordData?>): CompletableResultCode {
        return if (!CracConfig.isRestored)
            CompletableResultCode.ofSuccess()
        else base.export(logRecords)
    }

    override fun flush(): CompletableResultCode = base.flush()
    override fun shutdown(): CompletableResultCode = base.shutdown()
}