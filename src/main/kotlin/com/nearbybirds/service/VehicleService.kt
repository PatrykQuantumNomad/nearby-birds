package com.nearbybirds.service

import com.nearbybirds.model.NearbyVehicle
import com.nearbybirds.model.SearchCenter
import com.nearbybirds.model.SearchResponse
import com.nearbybirds.repository.VehicleRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class VehicleService(
    private val vehicleRepository: VehicleRepository,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(VehicleService::class.java)

    fun searchNearby(lat: Double, lng: Double, radiusMeters: Double, limit: Int): SearchResponse {
        logger.debug(
            "Searching for vehicles: lat={}, lng={}, radius={}m, limit={}",
            lat, lng, radiusMeters, limit
        )

        val projections = vehicleRepository.findNearbyAvailableVehicles(lat, lng, radiusMeters, limit)

        val vehicles = projections.map { p ->
            NearbyVehicle(
                birdId = p.getBirdId(),
                latitude = p.getLatitude(),
                longitude = p.getLongitude(),
                distanceMeters = Math.round(p.getDistanceMeters() * 10.0) / 10.0
            )
        }

        // Record the result set size for observability
        meterRegistry.summary("nearby.search.results.count").record(vehicles.size.toDouble())

        logger.info(
            "Search completed: lat={}, lng={}, radius={}m, results={}",
            lat, lng, radiusMeters, vehicles.size
        )

        return SearchResponse(
            vehicles = vehicles,
            count = vehicles.size,
            searchCenter = SearchCenter(lat, lng),
            searchRadiusMeters = radiusMeters
        )
    }
}
