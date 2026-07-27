package com.ghost.playground.features

import com.ghost.playground.ui.icons.PlaygroundIconKind

/** A Studio preset: a real, KSP-compiled DTO demonstrated against one or more [LabVariant] payloads. */
data class FeatureLab(
    val id: String,
    val icon: PlaygroundIconKind,
    val titleEn: String,
    val titleEs: String,
    val introEn: String,
    val introEs: String,
    /** Real Kotlin source for the annotated DTO below — shown read-only, this *is* what runs. */
    val dtoSource: String,
    /** Field names for the dispatch-table preview; empty skips that card (e.g. sealed types). */
    val fieldNames: List<String>,
    val variants: List<LabVariant>,
    val run: (json: String) -> String,
    val explainEn: (input: String, output: String) -> String,
    val explainEs: (input: String, output: String) -> String,
)
