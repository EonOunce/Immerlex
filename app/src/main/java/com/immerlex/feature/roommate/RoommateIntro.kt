package com.immerlex.feature.roommate

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.roommate.PersonaCards
import com.immerlex.core.roommate.RoommateSetupStage
import java.io.File

/** 一张表单里那五个字段；界面只管填，取上限由 core 决定。 */
data class PersonaDraft(
    val name: String = "",
    val nature: String = PersonaCards.DIRECT,
    val pace: String = PersonaCards.SLOW,
    val face: String = PersonaCards.OPEN,
    val setting: String = "",
)

/** 初识：先看见一间空房，再给它名字、三样底色，和多留一句的设定。 */
@Composable
fun RoommateIntro(onCreate: (name: String, nature: String, pace: String, face: String, setting: String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RoomTeaser(breathing = false)
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
        PersonaForm(PersonaDraft(), R.string.room_create) { draft ->
            onCreate(draft.name, draft.nature, draft.pace, draft.face, draft.setting)
        }
    }
}

/** 建档要等模型：还是那间房，只是它在里面动。 */
@Composable
fun RoommateCreating(stage: RoommateSetupStage?) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RoomTeaser(breathing = true)
        Text(
            stringResource(R.string.room_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(createStageLabel(stage)),
            style = MaterialTheme.typography.bodyMedium,
            color = ImmerlexColors.Body,
        )
    }
}

/** 初识和名片共用的一张表单：只有按钮上的字与下面的说明不同。 */
@Composable
fun PersonaForm(
    initial: PersonaDraft,
    submitLabel: Int,
    note: Int? = null,
    onSubmit: (PersonaDraft) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var nature by rememberSaveable { mutableStateOf(initial.nature) }
    var pace by rememberSaveable { mutableStateOf(initial.pace) }
    var face by rememberSaveable { mutableStateOf(initial.face) }
    var setting by rememberSaveable { mutableStateOf(initial.setting) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
        OutlinedTextField(
            value = setting,
            onValueChange = { setting = it.take(80) },
            label = { Text(stringResource(R.string.room_setting_label)) },
            supportingText = { Text(stringResource(R.string.room_setting_hint)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSubmit(PersonaDraft(name, nature, pace, face, setting)) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(submitLabel))
        }
        if (note != null) {
            Text(
                stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = ImmerlexColors.Meta,
            )
        }
    }
}

/** 一间房的缩略图：还没认识时是空的，认识之后放它此刻的动图。 */
@Composable
fun RoomTeaser(breathing: Boolean, file: File? = null) {
    val alpha = if (!breathing) 1f else {
        val transition = rememberInfiniteTransition(label = "room")
        transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "roomBreath",
        ).value
    }
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
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight(0.56f)
                    .padding(bottom = 14.dp),
            ) {
                if (file != null) {
                    GifStage(file, Modifier.fillMaxSize())
                } else {
                    PetSilhouette(Modifier.fillMaxSize().alpha(alpha))
                }
            }
        }
    }
}

private fun createStageLabel(stage: RoommateSetupStage?): Int = when (stage) {
    RoommateSetupStage.LINEBOOK -> R.string.room_setup_linebook
    else -> R.string.room_setup_first_line
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
