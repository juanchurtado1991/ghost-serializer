package com.ghost.serialization.sample.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ghost.serialization.sample.api.RickAndMortyApi
import com.ghost.serialization.sample.domain.Character
import kotlinx.coroutines.launch

@Composable
fun GhostSampleApp() {
    val api = remember { RickAndMortyApi() }
    val scope = rememberCoroutineScope()
    
    var characters by remember { mutableStateOf<List<Character>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var executionTimeMs by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustrialDesignSystem.BackgroundGradient) // Using the majestic gradient
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // Header Section
            IndustrialText(
                text = "GHOST SERIALIZATION",
                isBold = true,
                fontSize = 28,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            IndustrialText(
                text = "Industrial Multiplatform Architecture Demo",
                isSecondary = true,
                fontSize = 14,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Action Section
            IndustrialButton(
                text = "FETCH COMPLEX API (JSON)",
                onClick = {
                    if (isLoading) return@IndustrialButton
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        val start = kotlin.time.TimeSource.Monotonic.markNow()
                        
                        val result = api.getCharacters()
                        
                        val end = kotlin.time.TimeSource.Monotonic.markNow()
                        
                        result.onSuccess { res ->
                            characters = res.results
                            executionTimeMs = (end - start).inWholeMilliseconds
                        }.onFailure { err ->
                            errorMessage = err.message
                        }
                        
                        isLoading = false
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            // Metrics Dashboard
            AnimatedVisibility(
                visible = executionTimeMs > 0 && !isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IndustrialDesignSystem.PrimaryAccent.copy(alpha = 0.1f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IndustrialText(
                            text = "⚡ HYPER-PERFORMANCE METRICS",
                            isBold = true,
                            fontSize = 12,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        IndustrialText(
                            text = "Ghost Engine parsed ${characters.size} nested objects in ${executionTimeMs}ms",
                            isSecondary = false,
                            fontSize = 15,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        IndustrialText(
                            text = "Time includes cross-platform Ktor Network I/O",
                            isSecondary = true,
                            fontSize = 11
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Content Area
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = IndustrialDesignSystem.AccentGlow,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else if (errorMessage != null) {
                    IndustrialText(
                        text = "NETWORK ERROR: \n$errorMessage",
                        isBold = true,
                        fontSize = 14,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(characters) { character ->
                            CharacterCard(character)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(character: Character) {
    IndustrialCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simulated Avatar Placeholder (Using first letter context)
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(IndustrialDesignSystem.BorderColor),
                contentAlignment = Alignment.Center
            ) {
                IndustrialText(
                    text = character.name.take(1).uppercase(),
                    isBold = true,
                    fontSize = 24
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                IndustrialText(
                    text = character.name, 
                    isBold = true, 
                    fontSize = 18,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                StatusIndicator(character.status.name)
                Spacer(modifier = Modifier.height(10.dp))
                IndustrialRow("Species:", character.species)
                IndustrialRow("Origin:", character.origin.name)
            }
        }
    }
}
