package com.ghost.playground.features

import com.ghost.playground.i18n.Strings
import com.ghost.playground.ui.icons.PlaygroundIconKind

/** Studio preset for a KSP-compiled DTO demonstrated against one or more [LabVariant] payloads. */
data class FeatureLab(
    val id: String,
    val icon: PlaygroundIconKind,
    val titleEn: String,
    val titleEs: String,
    val introEn: String,
    val introEs: String,
    /** Wire format for the input card label and pipeline step text; defaults to JSON. */
    val wireFormat: LabWireFormat = LabWireFormat.JSON,
    /** Kotlin source for the annotated DTO; shown read-only and executed by the pipeline. */
    val dtoSource: String,
    /** Field names shown in the dispatch-table preview; an empty list hides that card. */
    val fieldNames: List<String>,
    val variants: List<LabVariant>,
    val run: (json: String) -> String,
    val explainEn: (input: String, output: String) -> String,
    val explainEs: (input: String, output: String) -> String,
) {
    fun inputLabel(strings: Strings): String = wireFormat.inputLabel(strings)

    fun pipelineRunDetail(strings: Strings): String = wireFormat.pipelineRunDetail(strings)

    fun pipelineRunTitle(strings: Strings): String = wireFormat.pipelineRunTitle(strings)
}
