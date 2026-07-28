package com.ghost.playground.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.features.FeatureCatalog
import com.ghost.playground.hash.PerfectHashLab
import com.ghost.playground.i18n.Lang
import com.ghost.playground.i18n.Strings
import com.ghost.playground.ui.icons.PlaygroundIcon
import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.playground.ui.theme.Coral
import com.ghost.playground.ui.theme.Ink
import com.ghost.playground.ui.theme.InkMuted
import com.ghost.playground.ui.theme.Rose
import com.ghost.playground.ui.theme.Sage
import com.ghost.playground.ui.theme.Teal
import com.ghost.playground.ui.theme.TealDark
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val PipelineStepDelay = 350.milliseconds

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(strings: Strings, lang: Lang) {
    var selectedLab by remember { mutableStateOf(FeatureCatalog.labs.first()) }
    var selectedVariant by remember(selectedLab.id) { mutableStateOf(selectedLab.variants.first()) }
    var running by remember { mutableStateOf(false) }
    var activeStep by remember { mutableIntStateOf(-1) }
    var output by remember(selectedLab.id, selectedVariant.id) { mutableStateOf<String?>(null) }
    var explanation by remember(selectedLab.id, selectedVariant.id) { mutableStateOf<String?>(null) }
    var runError by remember(selectedLab.id, selectedVariant.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        runError = null
        output = null
        explanation = null
        activeStep = 0
        delay(PipelineStepDelay)
        val json = selectedVariant.json
        try {
            val out = selectedLab.run(json)
            activeStep = 1
            delay(PipelineStepDelay)
            output = out
            explanation = if (lang == Lang.EN) {
                selectedLab.explainEn(json, out)
            } else {
                selectedLab.explainEs(json, out)
            }
            activeStep = 2
        } catch (e: Throwable) {
            runError = e.message ?: e.toString()
        }
        running = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.presets, color = InkMuted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
            FeatureCatalog.labs.forEach { lab ->
                PresetButton(if (lang == Lang.EN) lab.titleEn else lab.titleEs, selected = selectedLab.id == lab.id) {
                    selectedLab = lab
                    running = false
                    activeStep = -1
                }
            }
        }

        Text(
            if (lang == Lang.EN) selectedLab.introEn else selectedLab.introEs,
            style = MaterialTheme.typography.bodyLarge,
        )

        VariantSelector(
            variants = selectedLab.variants,
            selected = selectedVariant,
            isEnglish = lang == Lang.EN,
            label = strings.variantLabel,
        ) { variant ->
            selectedVariant = variant
            running = false
            activeStep = -1
        }

        Card(title = strings.dtoSource, accent = Teal, leadingIcon = selectedLab.icon) {
            CodeArea(selectedLab.dtoSource)
        }

        Card(title = selectedLab.inputLabel(strings), accent = Coral) {
            CodeArea(selectedVariant.json)
        }

        HeroButton(
            label = if (running) strings.running else strings.runPipeline,
            icon = null,
            colors = listOf(Teal, TealDark),
            enabled = !running,
        ) { running = true }

        runError?.let { err ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlaygroundIcon(PlaygroundIconKind.Warning, tint = Rose, size = 18.dp)
                Text(err, color = Rose, fontWeight = FontWeight.SemiBold)
            }
        }

        if (running || output != null) {
            Card(title = strings.pipelineTitle, accent = Sage) {
                PipelineRow(1, strings.pipelineStepReadTitle, strings.pipelineStepReadDetail, StepStatus.Done, true)
                PipelineRow(
                    2,
                    selectedLab.pipelineRunTitle(strings),
                    if (activeStep >= 1) selectedLab.pipelineRunDetail(strings) else strings.speedTestLoading,
                    if (activeStep >= 1) StepStatus.Done else StepStatus.Active,
                    activeStep >= 1,
                )
            }

            if (selectedLab.fieldNames.isNotEmpty()) {
                Card(title = strings.dispatchTitle, accent = Teal) {
                    val (slots, hashSummary) = remember(selectedLab.id) {
                        PerfectHashLab.dispatchPreview(selectedLab.fieldNames)
                    }
                    Text(hashSummary, color = TealDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        slots.forEach { slot ->
                            DispatchCell(slot.index, slot.fieldName, slot.occupied)
                        }
                    }
                }
            }
        }

        output?.let { out ->
            Card(title = strings.ghostOutput, accent = Teal) {
                CodeArea(out)
                Text(strings.whatHappened, fontWeight = FontWeight.Bold, color = Ink)
                Text(explanation.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
