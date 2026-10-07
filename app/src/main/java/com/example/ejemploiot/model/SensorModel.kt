package com.example.ejemploiot.model

data class SensorModel(
    val id: String = "",
    val name: String = "",
    val location: String = "",
    val hasMotion: Boolean = false,
    val lastMotionTimestamp: Long = 0L
)
