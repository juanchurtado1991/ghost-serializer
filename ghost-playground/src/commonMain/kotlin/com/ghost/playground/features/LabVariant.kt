package com.ghost.playground.features

/** One JSON payload a [FeatureLab] can run — lets a single DTO/annotation be shown under several real-world inputs. */
data class LabVariant(
    val id: String,
    val labelEn: String,
    val labelEs: String,
    val json: String,
)
