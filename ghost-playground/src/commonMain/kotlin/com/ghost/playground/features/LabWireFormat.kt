package com.ghost.playground.features

import com.ghost.playground.i18n.Strings

/**
 * Wire format a [FeatureLab] demonstrates. Each constant carries its own [Strings] selectors for
 * the input card label and pipeline step text, so adding a new wire format never requires editing
 * a `when` in [FeatureLab] — only adding one more constant here.
 */
enum class LabWireFormat(
    val inputLabel: (Strings) -> String,
    val pipelineRunTitle: (Strings) -> String,
    val pipelineRunDetail: (Strings) -> String,
) {
    JSON(
        inputLabel = { it.jsonInput },
        pipelineRunTitle = { it.pipelineStepRunTitle },
        pipelineRunDetail = { it.pipelineStepRunDetail }
    ),
    PROTO_JSON(
        inputLabel = { it.protoJsonInput },
        pipelineRunTitle = { it.pipelineStepRunTitleProto },
        pipelineRunDetail = { it.pipelineStepRunDetailProto }
    ),
    YAML(
        inputLabel = { it.yamlInput },
        pipelineRunTitle = { it.pipelineStepRunTitleYaml },
        pipelineRunDetail = { it.pipelineStepRunDetailYaml }
    ),
}
