package com.nearbybirds.service

import com.nearbybirds.repository.NearbyVehicleProjection
import com.nearbybirds.repository.VehicleRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VehicleServiceTest {

    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var vehicleService: VehicleService

    @BeforeEach
    fun setUp() {
        vehicleRepository = mock()
        meterRegistry = SimpleMeterRegistry()
        vehicleService = VehicleService(vehicleRepository, meterRegistry)
    }

    @Test
    fun `searchNearby returns mapped vehicles from repository`() {
        val projection = mockProjection("BIRD-001", 34.05, -118.25, 142.7)
        whenever(vehicleRepository.findNearbyAvailableVehicles(any(), any(), any(), any()))
            .thenReturn(listOf(projection))

        val result = vehicleService.searchNearby(34.0, -118.0, 500.0, 20)

        assertEquals(1, result.count)
        assertEquals("BIRD-001", result.vehicles[0].birdId)
        assertEquals(34.05, result.vehicles[0].latitude)
        assertEquals(-118.25, result.vehicles[0].longitude)
        assertEquals(142.7, result.vehicles[0].distanceMeters)
    }

    @Test
    fun `searchNearby returns empty list when no vehicles found`() {
        whenever(vehicleRepository.findNearbyAvailableVehicles(any(), any(), any(), any()))
            .thenReturn(emptyList())

        val result = vehicleService.searchNearby(0.0, 0.0, 500.0, 20)

        assertEquals(0, result.count)
        assertTrue(result.vehicles.isEmpty())
    }

    @Test
    fun `searchNearby populates search center from input coordinates`() {
        whenever(vehicleRepository.findNearbyAvailableVehicles(any(), any(), any(), any()))
            .thenReturn(emptyList())

        val result = vehicleService.searchNearby(34.0195, -118.4912, 1000.0, 10)

        assertEquals(34.0195, result.searchCenter.latitude)
        assertEquals(-118.4912, result.searchCenter.longitude)
        assertEquals(1000.0, result.searchRadiusMeters)
    }

    @Test
    fun `searchNearby rounds distance to one decimal place`() {
        val projection = mockProjection("BIRD-001", 34.05, -118.25, 142.7567)
        whenever(vehicleRepository.findNearbyAvailableVehicles(any(), any(), any(), any()))
            .thenReturn(listOf(projection))

        val result = vehicleService.searchNearby(34.0, -118.0, 500.0, 20)

        assertEquals(142.8, result.vehicles[0].distanceMeters)
    }

    @Test
    fun `searchNearby records result count metric`() {
        val projections = listOf(
            mockProjection("BIRD-001", 34.05, -118.25, 100.0),
            mockProjection("BIRD-002", 34.06, -118.26, 200.0)
        )
        whenever(vehicleRepository.findNearbyAvailableVehicles(any(), any(), any(), any()))
            .thenReturn(projections)

        vehicleService.searchNearby(34.0, -118.0, 500.0, 20)

        val summary = meterRegistry.find("nearby.search.results.count").summary()
        assertNotNull(summary)
        assertEquals(1L, summary!!.count())
        assertEquals(2.0, summary.totalAmount())
    }

    private fun mockProjection(birdId: String, lat: Double, lng: Double, distance: Double): NearbyVehicleProjection {
        return object : NearbyVehicleProjection {
            override fun getBirdId() = birdId
            override fun getLatitude() = lat
            override fun getLongitude() = lng
            override fun getDistanceMeters() = distance
        }
    }
}
