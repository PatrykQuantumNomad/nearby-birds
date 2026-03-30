package com.nearbybirds

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NearbyBirdsApplication

fun main(args: Array<String>) {
    runApplication<NearbyBirdsApplication>(*args)
}
