package com.immerlex.feature.roommate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.FishIcon
import com.immerlex.core.db.RM_CAUSE_ABSENCE
import com.immerlex.core.db.RM_CAUSE_FIRST
import com.immerlex.core.db.RM_CAUSE_RETURN
import com.immerlex.core.db.RM_CAUSE_STREAK
import com.immerlex.core.db.RM_CAUSE_WHIM
import com.immerlex.core.roommate.PersonaCards

@Composable
fun personaKeyLabel(key: String): String {
    val res = when (key) {
        PersonaCards.DIRECT -> R.string.room_nature_direct
        PersonaCards.INDIRECT -> R.string.room_nature_indirect
        PersonaCards.QUIET -> R.string.room_nature_quiet
        PersonaCards.SLOW -> R.string.room_pace_slow
        PersonaCards.FAST -> R.string.room_pace_fast
        PersonaCards.MOOD -> R.string.room_pace_mood
        PersonaCards.OPEN -> R.string.room_face_open
        PersonaCards.PLAIN -> R.string.room_face_plain
        PersonaCards.PERFORMS -> R.string.room_face_performs
        else -> R.string.room_title
    }
    return stringResource(res)
}

@Composable
fun giftCauseLabel(cause: String): String = stringResource(
    when (cause) {
        RM_CAUSE_FIRST -> R.string.room_cause_first
        RM_CAUSE_RETURN -> R.string.room_cause_return
        RM_CAUSE_STREAK -> R.string.room_cause_streak
        RM_CAUSE_ABSENCE -> R.string.room_cause_absence
        RM_CAUSE_WHIM -> R.string.room_cause_whim
        else -> R.string.room_cause_whim
    },
)

@Composable
fun FishPrice(price: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FishIcon(
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            price.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 房间里的三个动作和一个礼物盒，都用线条画；图标库里没有合适的现成符号。 */
enum class RoomGlyph { HEART, BUBBLE, DOOR, GIFT }

@Composable
fun Glyph(
    glyph: RoomGlyph,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Canvas(modifier) {
        when (glyph) {
            RoomGlyph.HEART -> drawHeart(tint)
            RoomGlyph.BUBBLE -> drawBubble(tint)
            RoomGlyph.DOOR -> drawDoor(tint)
            RoomGlyph.GIFT -> drawGift(tint)
        }
    }
}

private fun DrawScope.drawHeart(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.5f, h * 0.86f)
        cubicTo(w * 0.06f, h * 0.56f, w * 0.06f, h * 0.16f, w * 0.30f, h * 0.16f)
        cubicTo(w * 0.42f, h * 0.16f, w * 0.5f, h * 0.28f, w * 0.5f, h * 0.34f)
        cubicTo(w * 0.5f, h * 0.28f, w * 0.58f, h * 0.16f, w * 0.70f, h * 0.16f)
        cubicTo(w * 0.94f, h * 0.16f, w * 0.94f, h * 0.56f, w * 0.5f, h * 0.86f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawBubble(color: Color) {
    val w = size.width
    val h = size.height
    val body = Path().apply {
        addRoundRect(
            RoundRect(
                w * 0.08f,
                h * 0.10f,
                w * 0.92f,
                h * 0.68f,
                CornerRadius(w * 0.22f, w * 0.22f),
            ),
        )
    }
    drawPath(body, color)
    val tail = Path().apply {
        moveTo(w * 0.28f, h * 0.62f)
        lineTo(w * 0.46f, h * 0.62f)
        lineTo(w * 0.30f, h * 0.88f)
        close()
    }
    drawPath(tail, color)
}

private fun DrawScope.drawDoor(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.09f
    drawRoundRect(
        color,
        Offset(w * 0.14f, h * 0.08f),
        Size(w * 0.44f, h * 0.84f),
        CornerRadius(w * 0.08f),
        style = Stroke(stroke),
    )
    drawCircle(color, w * 0.035f, Offset(w * 0.48f, h * 0.52f))
    drawLine(
        color,
        Offset(w * 0.68f, h * 0.5f),
        Offset(w * 0.92f, h * 0.5f),
        strokeWidth = stroke,
    )
    val arrow = Path().apply {
        moveTo(w * 0.80f, h * 0.34f)
        lineTo(w * 0.94f, h * 0.5f)
        lineTo(w * 0.80f, h * 0.66f)
    }
    drawPath(arrow, color, style = Stroke(stroke))
}

private fun DrawScope.drawGift(color: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color,
        Offset(w * 0.14f, h * 0.36f),
        Size(w * 0.72f, h * 0.54f),
        CornerRadius(w * 0.08f),
    )
    drawRoundRect(
        color,
        Offset(w * 0.08f, h * 0.24f),
        Size(w * 0.84f, h * 0.18f),
        CornerRadius(w * 0.05f),
    )
    drawRect(Color.White.copy(alpha = 0.55f), Offset(w * 0.44f, h * 0.24f), Size(w * 0.12f, h * 0.66f))
    drawCircle(color, w * 0.13f, Offset(w * 0.36f, h * 0.15f))
    drawCircle(color, w * 0.13f, Offset(w * 0.64f, h * 0.15f))
}
