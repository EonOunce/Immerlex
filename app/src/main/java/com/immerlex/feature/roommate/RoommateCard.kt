package com.immerlex.feature.roommate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import java.io.File

/**
 * 它的名片：改的是它怎么说话。保存只会让它重说一次第一句话、把台词本重写一遍，
 * 攒下的小鱼干、小本子、它送的东西和已经买下的都不跟着重来。
 */
@Composable
fun RoommateCardPage(
    draft: PersonaDraft,
    stageFile: File?,
    onUpdate: (name: String, nature: String, pace: String, face: String, setting: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.room_card_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        RoomTeaser(breathing = false, file = stageFile)
        PersonaForm(draft, R.string.room_card_save, R.string.room_card_note) { next ->
            onUpdate(next.name, next.nature, next.pace, next.face, next.setting)
        }
    }
}
