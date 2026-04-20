package com.ghost.serialization.sample.ui.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ghost.serialization.sample.ui.AppDesign
import com.ghost.serialization.sample.ui.model.NetworkStack

@Composable
fun StackSelectorDialog(
    current: NetworkStack,
    onSelect: (NetworkStack) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppDesign.SurfaceColor,
            border = BorderStroke(1.dp, AppDesign.GlassBorder)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                SampleText(
                    text = "SELECT NETWORK STACK",
                    isBold = true,
                    fontSize = 18,
                    overrideColor = AppDesign.AccentGlow
                )
                SampleText(
                    text = "Choose the engine used for the initial data fetch.",
                    fontSize = 12,
                    isSecondary = true,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                NetworkStack.entries.forEach { stack ->
                    val isSelected = stack == current
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(stack) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            AppDesign.AccentGlow.copy(alpha = 0.1f)
                        } else {
                            Color.Transparent
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) {
                                AppDesign.AccentGlow
                            } else {
                                AppDesign.GlassBorder
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SampleText(
                                text = stack.title,
                                isBold = true,
                                fontSize = 14,
                                overrideColor = if (isSelected) AppDesign.AccentGlow else AppDesign.TextPrimary
                            )
                            SampleText(text = stack.description, fontSize = 10, isSecondary = true)
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(top = 16.dp)
                ) {
                    SampleText(
                        text = "CLOSE",
                        isBold = true,
                        fontSize = 14,
                        overrideColor = AppDesign.AccentGlow
                    )
                }
            }
        }
    }
}
