package com.ghost.serialization.sample.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ghost.serialization.sample.domain.GhostCharacter

@Composable
fun CharacterCard(character: GhostCharacter) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = character.image,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f)
            ) {
                SampleText(
                    text = character.name,
                    isBold = true, fontSize = 16
                )
                StatusIndicator(
                    status = character.status.name
                )
                SampleText(
                    text = character.species,
                    fontSize = 12, isSecondary = true
                )
            }
        }
    }
}
