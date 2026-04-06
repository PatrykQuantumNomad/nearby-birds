package com.nearbybirds.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.UUID

/**
 * Interceptor that adds a unique request_id to the MDC for structured logging
 * and tracks per-status-code request counts.
 */
class RequestMetricsInterceptor(
    private val meterRegistry: MeterRegistry
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val requestId = request.getHeader("X-Request-Id") ?: UUID.randomUUID().toString()
        MDC.put("request_id", requestId)
        request.setAttribute("startTime", System.nanoTime())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        val status = response.status.toString()
        meterRegistry.counter("http.server.requests.status", "status", status).increment()

        val startTime = request.getAttribute("startTime") as? Long
        if (startTime != null) {
            val duration = System.nanoTime() - startTime
            Timer.builder("http.server.requests.duration")
                .tag("status", status)
                .tag("method", request.method)
                .tag("uri", request.requestURI)
                .register(meterRegistry)
                .record(duration, java.util.concurrent.TimeUnit.NANOSECONDS)
        }

        if (ex != null) {
            meterRegistry.counter("nearby.search.requests", "status", "error").increment()
        }

        MDC.clear()
    }
}

@Configuration
class WebMvcConfig(
    private val meterRegistry: MeterRegistry
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(RequestMetricsInterceptor(meterRegistry))
    }
}
