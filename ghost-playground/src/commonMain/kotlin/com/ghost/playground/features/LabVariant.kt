package com.ghost.playground.features

/** Sample JSON payload that a [FeatureLab] can execute under a single DTO or annotation preset. */
data class LabVariant(
    val id: String,
    val labelEn: String,
    val labelEs: String,
    val json: String,
)
