package com.nearbybirds.model

import jakarta.persistence.*
import org.locationtech.jts.geom.Point
import java.time.Instant

@Entity
@Table(name = "vehicles")
class Vehicle(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "bird_id", nullable = false, unique = true)
    val birdId: String,

    @Column(name = "location", nullable = false, columnDefinition = "geography(Point, 4326)")
    var location: Point,

    @Column(name = "battery_pct", nullable = false)
    var batteryPct: Short = 100,

    @Column(name = "available", nullable = false)
    var available: Boolean = true,

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),

    @Column(name = "city", nullable = false)
    val city: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
