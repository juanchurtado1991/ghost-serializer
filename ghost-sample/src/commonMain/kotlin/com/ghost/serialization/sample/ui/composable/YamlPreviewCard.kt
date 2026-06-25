package com.ghost.serialization.sample.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.serialization.sample.ui.AppDesign

// ── Editor color palette ─────────────────────────────────────────────────────

private val COLOR_EDITOR_BG       = Color(0xFF0D1117)
private val COLOR_EDITOR_BORDER   = Color(0xFF30363D)
private val COLOR_EDITOR_LINE_NUM = Color(0xFF484F58)
private val COLOR_YAML_KEY        = Color(0xFF79C0FF)
private val COLOR_YAML_STRING     = Color(0xFFA5D6FF)
private val COLOR_YAML_NUMBER     = Color(0xFFFFA657)
private val COLOR_YAML_BOOL       = Color(0xFFFF7B72)
private val COLOR_YAML_DASH       = Color(0xFFD2A8FF)
private val COLOR_YAML_DOC        = Color(0xFF7EE787)
private val COLOR_YAML_COMMENT    = Color(0xFF8B949E)

private const val MAX_PREVIEW_LINES = 80

@Composable
fun YamlPreviewCard(
    yamlText: String,
    onCopy: () -> Unit
) {
    val allLines = remember(yamlText) { yamlText.lines() }
    val displayLines = remember(allLines) { allLines.take(MAX_PREVIEW_LINES) }
    val remainingCount = remember(allLines) { (allLines.size - MAX_PREVIEW_LINES).coerceAtLeast(0) }
    val totalLines = allLines.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(COLOR_EDITOR_BG)
            .border(1.dp, COLOR_EDITOR_BORDER, RoundedCornerShape(16.dp))
    ) {
        // ── Title bar ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrafficDot(Color(0xFFFF5F56))
                Spacer(Modifier.width(6.dp))
                TrafficDot(Color(0xFFFFBD2E))
                Spacer(Modifier.width(6.dp))
                TrafficDot(Color(0xFF27C93F))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "ghost-output.yaml",
                    color = COLOR_EDITOR_LINE_NUM,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "($totalLines lines)",
                    color = COLOR_EDITOR_LINE_NUM,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            TextButton(onClick = onCopy) {
                Text(
                    text = "COPY",
                    color = COLOR_YAML_KEY,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp
                )
            }
        }

        HorizontalDivider(color = COLOR_EDITOR_BORDER, thickness = 1.dp)

        // ── Code content ───────────────────────────────────────────────────
        val verticalScrollState = rememberScrollState()
        val horizontalScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .verticalScroll(verticalScrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(vertical = 12.dp)
        ) {
            Column {
                displayLines.forEachIndexed { lineIndex, line ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Line number gutter
                        Text(
                            text = "${lineIndex + 1}",
                            color = COLOR_EDITOR_LINE_NUM,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(36.dp),
                            lineHeight = 18.sp
                        )
                        // Colored YAML line
                        Text(
                            text = buildYamlLine(line),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    }
                }

                if (remainingCount > 0) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width(36.dp))
                        Text(
                            text = "··· $remainingCount more lines ···",
                            color = COLOR_EDITOR_LINE_NUM,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // ── Footer stats ───────────────────────────────────────────────────
        HorizontalDivider(color = COLOR_EDITOR_BORDER, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UTF-8  ·  YAML 1.2  ·  Ghost Serializer",
                color = COLOR_EDITOR_LINE_NUM,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${yamlText.length} chars",
                color = COLOR_EDITOR_LINE_NUM,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun TrafficDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape)
    )
}

// ── YAML syntax coloring ─────────────────────────────────────────────────────
//
// Rules applied in order:
//  1. Document markers (--- / ...)
//  2. Comments (#)
//  3. List item dash "- " → dash purple, rest of line recurses
//  4. Key: value  → key sky-blue, colon white, value type-colored
//  5. Plain scalar (value only line) → type-colored
//

private fun buildYamlLine(line: String): AnnotatedString = buildAnnotatedString {
    val trimmed = line.trimStart()
    val indent = line.length - trimmed.length

    // Leading whitespace (preserve)
    if (indent > 0) append(line.substring(0, indent))

    when {
        // Document markers
        trimmed == "---" || trimmed == "..." -> {
            withStyle(SpanStyle(color = COLOR_YAML_DOC, fontWeight = FontWeight.Bold)) {
                append(trimmed)
            }
        }
        // Comment
        trimmed.startsWith("#") -> {
            withStyle(SpanStyle(color = COLOR_YAML_COMMENT)) {
                append(trimmed)
            }
        }
        // List item starting with "- "
        trimmed.startsWith("- ") -> {
            withStyle(SpanStyle(color = COLOR_YAML_DASH, fontWeight = FontWeight.Bold)) {
                append("- ")
            }
            appendYamlKeyOrValue(trimmed.substring(2))
        }
        // Bare dash (empty list item)
        trimmed == "-" -> {
            withStyle(SpanStyle(color = COLOR_YAML_DASH, fontWeight = FontWeight.Bold)) {
                append("-")
            }
        }
        // Key: value or key: (nested mapping)
        else -> appendYamlKeyOrValue(trimmed)
    }
}

private fun AnnotatedString.Builder.appendYamlKeyOrValue(segment: String) {
    val colonIdx = segment.indexOf(": ")
    val isKeyOnly = segment.endsWith(":") && !segment.endsWith("::") && colonIdx == -1

    when {
        isKeyOnly -> {
            // "key:" with no value (nested mapping)
            val keyPart = segment.dropLast(1)
            withStyle(SpanStyle(color = COLOR_YAML_KEY)) { append(keyPart) }
            withStyle(SpanStyle(color = AppDesign.TextSecondary)) { append(":") }
        }
        colonIdx >= 0 -> {
            val keyPart = segment.substring(0, colonIdx)
            val valuePart = segment.substring(colonIdx + 2)
            withStyle(SpanStyle(color = COLOR_YAML_KEY)) { append(keyPart) }
            withStyle(SpanStyle(color = AppDesign.TextSecondary)) { append(": ") }
            appendTypedValue(valuePart)
        }
        else -> {
            // Plain scalar continuation line
            appendTypedValue(segment)
        }
    }
}

private fun AnnotatedString.Builder.appendTypedValue(value: String) {
    val trimmed = value.trim()
    val color = when {
        trimmed.isEmpty() -> return
        trimmed == "true" || trimmed == "false" || trimmed == "null" || trimmed == "~" ->
            COLOR_YAML_BOOL
        trimmed.first().isDigit() || (trimmed.first() == '-' && trimmed.length > 1 && trimmed[1].isDigit()) ->
            COLOR_YAML_NUMBER
        trimmed.startsWith("'") || trimmed.startsWith("\"") ->
            COLOR_YAML_STRING
        else -> AppDesign.TextPrimary
    }
    withStyle(SpanStyle(color = color)) { append(value) }
}
