package com.ghost.serialization.sample.ui.composable.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ghost.serialization.sample.ui.AppDesign

@Composable
fun RawPayloadViewer(
    showRawJson: Boolean,
    standardJson: String,
    proto3Json: String?,
    showProto3Json: Boolean,
    onToggleViewer: () -> Unit,
    onToggleProto3: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppDesign.SurfaceColor,
        border = BorderStroke(1.dp, AppDesign.GlassBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SampleText(
                    text = "RAW PAYLOAD VIEWER",
                    isBold = true,
                    fontSize = 11,
                    isSecondary = true
                )
                TextButton(onClick = onToggleViewer) {
                    SampleText(
                        text = if (showRawJson) "HIDE" else "SHOW",
                        fontSize = 11,
                        isBold = true,
                        overrideColor = AppDesign.AccentGlow
                    )
                }
            }

            if (showRawJson) {
                HorizontalDivider(
                    color = AppDesign.GlassBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (proto3Json != null) {
                    // Tab bar: Standard JSON | Proto3 JSON
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(false, true).forEach { isProto3 ->
                            val selected = showProto3Json == isProto3
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) AppDesign.AccentGlow.copy(alpha = 0.15f)
                                else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) AppDesign.AccentGlow.copy(alpha = 0.5f)
                                    else AppDesign.GlassBorder
                                )
                            ) {
                                TextButton(onClick = { if (!selected) onToggleProto3() }) {
                                    SampleText(
                                        text = if (isProto3) "PROTO3 JSON" else "STANDARD JSON",
                                        fontSize = 10,
                                        isBold = selected,
                                        overrideColor = if (selected) AppDesign.AccentGlow
                                        else AppDesign.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val label = if (proto3Json != null && showProto3Json) {
                    "PROTO3 JSON (numbers quoted as strings — gRPC-Gateway format)"
                } else if (proto3Json != null) {
                    "STANDARD REST JSON (as downloaded from OpenLibrary API)"
                } else {
                    "STANDARD REST JSON (as downloaded from Rick & Morty API)"
                }

                val rawJson = if (showProto3Json && proto3Json != null) proto3Json else standardJson

                SampleText(
                    text = label,
                    fontSize = 10,
                    overrideColor = if (showProto3Json && proto3Json != null) AppDesign.AccentGlow else AppDesign.AccentCompetitor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val previewLength = minOf(rawJson.length, 2000)
                SampleText(
                    text = rawJson.take(previewLength) +
                        if (rawJson.length > previewLength) "\n... (${rawJson.length} total chars)" else "",
                    fontSize = 10,
                    isSecondary = true
                )
            }
        }
    }
}
