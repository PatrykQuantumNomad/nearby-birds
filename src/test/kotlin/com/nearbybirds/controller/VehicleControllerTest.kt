package com.nearbybirds.controller

import com.nearbybirds.model.NearbyVehicle
import com.nearbybirds.model.SearchCenter
import com.nearbybirds.model.SearchResponse
import com.nearbybirds.service.VehicleService
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

@WebMvcTest(VehicleController::class)
@Import(VehicleControllerTest.TestMetricsConfig::class)
class VehicleControllerTest {

    @TestConfiguration
    class TestMetricsConfig {
        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var vehicleService: VehicleService

    @Test
    fun `GET nearby returns 200 with vehicle list`() {
        val response = SearchResponse(
            vehicles = listOf(
                NearbyVehicle("BIRD-001", 34.05, -118.25, 142.7)
            ),
            count = 1,
            searchCenter = SearchCenter(34.0, -118.0),
            searchRadiusMeters = 500.0
        )
        whenever(vehicleService.searchNearby(any(), any(), any(), any())).thenReturn(response)

        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", "34.0")
            param("lng", "-118.0")
        }.andExpect {
            status { isOk() }
            jsonPath("$.count") { value(1) }
            jsonPath("$.vehicles[0].birdId") { value("BIRD-001") }
            jsonPath("$.vehicles[0].distanceMeters") { value(142.7) }
            jsonPath("$.searchRadiusMeters") { value(500.0) }
        }
    }

    @Test
    fun `GET nearby with custom radius and limit`() {
        val response = SearchResponse(
            vehicles = emptyList(),
            count = 0,
            searchCenter = SearchCenter(34.0, -118.0),
            searchRadiusMeters = 1000.0
        )
        whenever(vehicleService.searchNearby(any(), any(), any(), any())).thenReturn(response)

        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", "34.0")
            param("lng", "-118.0")
            param("radius", "1000")
            param("limit", "50")
        }.andExpect {
            status { isOk() }
            jsonPath("$.count") { value(0) }
        }
    }

    @Test
    fun `GET nearby without required lat returns 400`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lng", "-118.0")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET nearby without required lng returns 400`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", "34.0")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET nearby with lat out of range returns 400`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", "95.0")
            param("lng", "-118.0")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET nearby with lng out of range returns 400`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", "34.0")
            param("lng", "-181.0")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET nearby with non-numeric lat returns 400`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", "abc")
            param("lng", "-118.0")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
