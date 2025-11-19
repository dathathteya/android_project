package com.example.smartplant

data class MoistureReading(
    val percentage: Int,
    val timestamp: String,
    val sensorStatus: String
)

enum class MoistureStatus {
    CRITICAL, LOW, OPTIMAL, HIGH, SATURATED
}

fun categorizeMoisture(p: Int): MoistureStatus {
    return when {
        p <= 20 -> MoistureStatus.CRITICAL
        p <= 40 -> MoistureStatus.LOW
        p <= 70 -> MoistureStatus.OPTIMAL
        p <= 90 -> MoistureStatus.HIGH
        else -> MoistureStatus.SATURATED
    }
}
