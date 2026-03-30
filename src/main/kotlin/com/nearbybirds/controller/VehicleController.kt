package com.nearbybirds.controller

import com.nearbybirds.model.SearchResponse
import com.nearbybirds.service.VehicleService
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/vehicles")
@Validated
class VehicleController(
    private val vehicleService: VehicleService,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(VehicleController::class.java)

    @GetMapping("/nearby")
    fun searchNearby(
        @RequestParam
        @DecimalMin("-90.0") @DecimalMax("90.0")
        lat: Double,

        @RequestParam
        @DecimalMin("-180.0") @DecimalMax("180.0")
        lng: Double,

        @RequestParam(defaultValue = "500.0")
        @DecimalMin("1.0") @DecimalMax("5000.0")
        radius: Double,

        @RequestParam(defaultValue = "20")
        @Min(1) @Max(100)
        limit: Int
    ): ResponseEntity<SearchResponse> {
        val timer = Timer.builder("nearby.search.latency")
            .tag("endpoint", "nearby")
            .register(meterRegistry)

        return timer.record<ResponseEntity<SearchResponse>> {
            meterRegistry.counter("nearby.search.requests", "status", "ok").increment()

            val response = vehicleService.searchNearby(lat, lng, radius, limit)
            ResponseEntity.ok(response)
        }!!
    }
}
