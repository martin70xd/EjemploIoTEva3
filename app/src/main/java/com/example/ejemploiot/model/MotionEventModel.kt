package com.example.ejemploiot.model

data class MotionEventModel(
    val id: String = "",
    val sensorId: String = "",
    val sensorName: String = "",
    val location: String = "",
    val timestamp: Long = 0L,
    val userEmail: String = ""
)
