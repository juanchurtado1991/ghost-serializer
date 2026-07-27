package com.ghost.playground.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghost.playground.i18n.I18n
import com.ghost.playground.i18n.Lang
import com.ghost.playground.ui.theme.GhostPlaygroundTheme
import com.ghost.playground.ui.theme.PageGradient

private val ContentMaxWidth = 960.dp
private const val TabFadeInDurationMs = 200
private const val TabFadeOutDurationMs = 120

@Composable
fun GhostPlaygroundApp() {
    var lang by remember { mutableStateOf(Lang.EN) }
    var dest by remember { mutableStateOf(PlaygroundDest.SpeedTest) }
    val strings = remember(lang) { I18n.of(lang) }

    GhostPlaygroundTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PageGradient)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(strings, lang, dest, onLang = { lang = it }, onNav = { dest = it })
            Spacer(Modifier.height(28.dp))
            AnimatedContent(
                targetState = dest,
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(tween(TabFadeInDurationMs)) togetherWith
                                    fadeOut(tween(TabFadeOutDurationMs)))
                        .using(SizeTransform(clip = false))
                },
                label = "tab",
            ) { tab ->
                Column(Modifier.fillMaxWidth()) {
                    when (tab) {
                        PlaygroundDest.SpeedTest -> SpeedTestScreen(strings)
                        PlaygroundDest.Studio -> StudioScreen(strings, lang)
                        PlaygroundDest.UnderHood -> WhyItsFastScreen(strings, lang)
                        PlaygroundDest.LearnMore -> DocsScreen(strings)
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
