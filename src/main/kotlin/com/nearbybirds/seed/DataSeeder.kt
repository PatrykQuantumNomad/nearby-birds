package com.nearbybirds.seed

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * Seeds the database with sample vehicle data for development and demonstration.
 * Active by default; disabled in test profile (tests manage their own data).
 *
 * Seeds vehicles in two cities:
 * - Santa Monica, CA (Bird's original home base)
 * - San Francisco, CA
 */
@Component
@Profile("!test")
class DataSeeder(
    private val jdbcTemplate: JdbcTemplate
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(DataSeeder::class.java)

    override fun run(vararg args: String?) {
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vehicles", Int::class.java) ?: 0
        if (count > 0) {
            logger.info("Database already seeded with {} vehicles, skipping", count)
            return
        }

        logger.info("Seeding database with sample vehicle data...")

        val cities = listOf(
            CityData("Santa Monica", 34.0195, -118.4912, 0.02),
            CityData("San Francisco", 37.7749, -122.4194, 0.03)
        )

        var total = 0
        for (city in cities) {
            val vehicleCount = 250
            for (i in 1..vehicleCount) {
                val birdId = "BIRD-%s-%05d".format(city.name.take(2).uppercase(), total + 1)
                val lat = city.centerLat + Random.nextDouble(-city.spread, city.spread)
                val lng = city.centerLng + Random.nextDouble(-city.spread, city.spread)
                val battery = Random.nextInt(5, 100).toShort()
                val available = battery >= 5 && Random.nextDouble() > 0.1 // 10% chance of being unavailable

                jdbcTemplate.update(
                    """
                    INSERT INTO vehicles (bird_id, location, battery_pct, available, city, last_seen_at)
                    VALUES (?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, NOW())
                    """,
                    birdId, lng, lat, battery, available, city.name
                )
                total++
            }
        }

        logger.info("Seeded {} vehicles across {} cities", total, cities.size)
    }

    private data class CityData(
        val name: String,
        val centerLat: Double,
        val centerLng: Double,
        val spread: Double
    )
}
