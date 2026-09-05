package com.eureka.cyclelens.detection

data class VisualDetection(
    val visualFormId: String,
    val confidence: Float,
    val timestampMillis: Long,
)
