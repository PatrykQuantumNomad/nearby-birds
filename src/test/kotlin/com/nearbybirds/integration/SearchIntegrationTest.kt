package com.nearbybirds.integration

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@Testcontainers
@ActiveProfiles("test")
class SearchIntegrationTest : TestPostgisContainer() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    // Santa Monica Pier: 34.0094, -118.4973
    private val pierLat = 34.0094
    private val pierLng = -118.4973

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM vehicles")
        seedTestVehicles()
    }

    private fun seedTestVehicles() {
        // Vehicle 1: ~100m from pier, available
        insertVehicle("BIRD-001", 34.0100, -118.4970, batteryPct = 80, available = true)
        // Vehicle 2: ~200m from pier, available
        insertVehicle("BIRD-002", 34.0110, -118.4965, batteryPct = 60, available = true)
        // Vehicle 3: ~50m from pier, NOT available (in a ride)
        insertVehicle("BIRD-003", 34.0097, -118.4975, batteryPct = 90, available = false)
        // Vehicle 4: ~5km from pier (far away), available
        insertVehicle("BIRD-004", 34.0500, -118.4500, batteryPct = 70, available = true)
        // Vehicle 5: ~300m from pier, available but low battery
        insertVehicle("BIRD-005", 34.0120, -118.4955, batteryPct = 8, available = true)
    }

    private fun insertVehicle(birdId: String, lat: Double, lng: Double, batteryPct: Int, available: Boolean) {
        jdbcTemplate.update(
            """
            INSERT INTO vehicles (bird_id, location, battery_pct, available, city, last_seen_at)
            VALUES (?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 'Santa Monica', NOW())
            """,
            birdId, lng, lat, batteryPct, available
        )
    }

    @Test
    fun `search nearby returns available vehicles within radius`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", pierLat.toString())
            param("lng", pierLng.toString())
            param("radius", "500")
        }.andExpect {
            status { isOk() }
            jsonPath("$.count") { value(3) } // BIRD-001, BIRD-002, BIRD-005 (not BIRD-003 unavailable, not BIRD-004 too far)
            jsonPath("$.searchRadiusMeters") { value(500.0) }
            jsonPath("$.vehicles[0].birdId") { exists() }
            jsonPath("$.vehicles[0].latitude") { exists() }
            jsonPath("$.vehicles[0].longitude") { exists() }
            jsonPath("$.vehicles[0].distanceMeters") { exists() }
        }
    }

    @Test
    fun `search nearby excludes unavailable vehicles`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", pierLat.toString())
            param("lng", pierLng.toString())
            param("radius", "5000")
        }.andExpect {
            status { isOk() }
            // BIRD-003 is unavailable and should not appear, even within radius
            jsonPath("$.vehicles[?(@.birdId == 'BIRD-003')]") { doesNotExist() }
        }
    }

    @Test
    fun `search nearby returns results ordered by distance ascending`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", pierLat.toString())
            param("lng", pierLng.toString())
            param("radius", "1000")
        }.andExpect {
            status { isOk() }
            // First result should be the closest vehicle
            jsonPath("$.vehicles[0].birdId") { value("BIRD-001") }
        }
    }

    @Test
    fun `search with small radius returns only very close vehicles`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", pierLat.toString())
            param("lng", pierLng.toString())
            param("radius", "120")
        }.andExpect {
            status { isOk() }
            jsonPath("$.count") { value(1) } // Only BIRD-001 is within ~100m
            jsonPath("$.vehicles[0].birdId") { value("BIRD-001") }
        }
    }

    @Test
    fun `search with limit restricts result count`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", pierLat.toString())
            param("lng", pierLng.toString())
            param("radius", "5000")
            param("limit", "2")
        }.andExpect {
            status { isOk() }
            jsonPath("$.count") { value(2) }
        }
    }

    @Test
    fun `search at location with no vehicles returns empty list`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", "0.0") // Middle of the ocean
            param("lng", "0.0")
            param("radius", "500")
        }.andExpect {
            status { isOk() }
            jsonPath("$.count") { value(0) }
            jsonPath("$.vehicles") { isEmpty() }
        }
    }

    @Test
    fun `search with missing lat returns 400`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lng", pierLng.toString())
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `search with invalid lat returns 400`() {
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", "91.0") // Out of range
            param("lng", pierLng.toString())
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `health liveness endpoint returns 200`() {
        mockMvc.get("/actuator/health/liveness").andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `health readiness endpoint returns 200`() {
        mockMvc.get("/actuator/health/readiness").andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `prometheus metrics endpoint returns 200`() {
        // First make a search to generate some metrics
        mockMvc.get("/api/v1/vehicles/nearby") {
            param("lat", pierLat.toString())
            param("lng", pierLng.toString())
        }

        mockMvc.get("/actuator/prometheus").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("nearby_search_latency")) }
        }
    }
}
