package com.example.mototrack.model

data class ModelMotor(
    val statusKelistrikan: Boolean = false,
    val statusStarter: Boolean = false,
    val statusBuzzer: Boolean = false,
    val statusGetar: Boolean = false
)
