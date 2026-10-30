package com.immerlex.feature.roommate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.roommate.PersonaCards

/** 初识：先看见一间空房，再给它名字和三样底色。 */
@Composable
fun RoommateIntro(onCreate: (name: String, nature: String, pace: String, face: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var nature by rememberSaveable { mutableStateOf(PersonaCards.DIRECT) }
    var pace by rememberSaveable { mutableStateOf(PersonaCards.SLOW) }
    var face by rememberSaveable { mutableStateOf(PersonaCards.OPEN) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = roomPalette().wall,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(28.dp)),
            ) {
                RoomBackdrop(Modifier.fillMaxSize())
                PetSilhouette(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp, start = 40.dp, end = 40.dp)
                        .size(120.dp),
                )
            }
        }
        Text(
            stringResource(R.string.room_intro_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.room_intro_lead),
            style = MaterialTheme.typography.bodyMedium,
            color = ImmerlexColors.Body,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(8) },
            label = { Text(stringResource(R.string.room_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ChipGroup(R.string.room_nature_label, PersonaCards.natureKeys, nature) { nature = it }
        ChipGroup(R.string.room_pace_label, PersonaCards.paceKeys, pace) { pace = it }
        ChipGroup(R.string.room_face_label, PersonaCards.faceKeys, face) { face = it }
        Button(
            onClick = { onCreate(name, nature, pace, face) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.room_create))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    labelRes: Int,
    keys: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = ImmerlexColors.Body,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            keys.forEach { key ->
                FilterChip(
                    selected = key == selected,
                    onClick = { onSelect(key) },
                    label = { Text(personaKeyLabel(key)) },
                )
            }
        }
    }
}
