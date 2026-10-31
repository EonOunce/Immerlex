package com.immerlex.feature.roommate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import java.io.File

/** 房间本体 + 气泡 + 它。点哪里都算摸了一下。 */
@Composable
fun RoomScene(
    file: File?,
    bubble: String,
    assetsImported: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(roomPalette().wall)
            .clickable(onClick = onTap),
    ) {
        RoomBackdrop(Modifier.fillMaxSize())
        SpeechBubble(
            text = bubble,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp, start = 18.dp, end = 18.dp)
                .widthIn(max = 320.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.64f)
                .fillMaxHeight(0.56f)
                .padding(bottom = 16.dp),
        ) {
            if (file != null) {
                GifStage(file, Modifier.fillMaxSize())
            } else {
                PetSilhouette(Modifier.fillMaxSize().padding(14.dp))
            }
        }
        if (file == null && assetsImported == 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                shape = CircleShape,
                color = roomPalette().bubble.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    stringResource(R.string.room_no_assets),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    val p = roomPalette()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = p.bubble,
            shadowElevation = 3.dp,
        ) {
            AnimatedContent(
                targetState = text,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "room-bubble",
            ) { line ->
                Text(
                    line,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
            }
        }
        Canvas(Modifier.size(width = 18.dp, height = 9.dp)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(tail, p.bubble)
        }
    }
}
