package com.nearbybirds.model

data class NearbyVehicle(
    val birdId: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double
)

data class SearchCenter(
    val latitude: Double,
    val longitude: Double
)

data class SearchResponse(
    val vehicles: List<NearbyVehicle>,
    val count: Int,
    val searchCenter: SearchCenter,
    val searchRadiusMeters: Double
)
