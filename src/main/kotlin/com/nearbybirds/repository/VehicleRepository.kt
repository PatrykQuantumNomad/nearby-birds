package com.nearbybirds.repository

import com.nearbybirds.model.Vehicle
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Projection interface for nearby vehicle search results.
 * PostGIS computes the distance server-side so we avoid fetching full entities.
 */
interface NearbyVehicleProjection {
    fun getBirdId(): String
    fun getLatitude(): Double
    fun getLongitude(): Double
    fun getDistanceMeters(): Double
}

@Repository
interface VehicleRepository : JpaRepository<Vehicle, Long> {

    /**
     * Finds available vehicles within [radiusMeters] of the given point,
     * ordered by distance ascending and limited to [limit] results.
     *
     * Uses ST_DWithin for index-accelerated radius filtering on the geography column,
     * and ST_Distance to compute exact distances for ordering and display.
     */
    @Query(
        value = """
            SELECT
                v.bird_id AS "birdId",
                ST_Y(v.location::geometry) AS "latitude",
                ST_X(v.location::geometry) AS "longitude",
                ST_Distance(v.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS "distanceMeters"
            FROM vehicles v
            WHERE v.available = TRUE
              AND ST_DWithin(v.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius)
            ORDER BY "distanceMeters" ASC
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findNearbyAvailableVehicles(
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        @Param("radius") radiusMeters: Double,
        @Param("limit") limit: Int
    ): List<NearbyVehicleProjection>
}
