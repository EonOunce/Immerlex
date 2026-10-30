@file:Suppress("DEPRECATION")

package com.immerlex.feature.roommate

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.immerlex.app.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 素材一直循环播放，摸着它说话时也不用从头再来——用户不用等一个动作播完。
 *
 * API 28 起用 [AnimatedImageDrawable]（`repeatCount = REPEAT_INFINITE`），26–27 用
 * [Movie] 加一个无限循环的线性动画器兜底。换素材才重新解码，同一条文件不会重播。
 *
 * 解码在 IO 线程，`start()` 回主线程调。
 */
@Composable
fun GifStage(
    file: File?,
    modifier: Modifier = Modifier,
) {
    var frame by remember { mutableStateOf<Any?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        frame = null
        failed = false
        val source = file ?: return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            runCatching { decode(source) }.getOrNull()
        }
        if (decoded == null) failed = true else frame = decoded
    }

    val current = frame
    if (current == null) {
        if (failed) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.room_stage_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        AnimatedStage(current, modifier)
    } else {
        (current as? Movie)?.let { movie -> MovieStage(movie, modifier) }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun AnimatedStage(frame: Any, modifier: Modifier) {
    val current = frame as? AnimatedImageDrawable ?: return
    DisposableEffect(current) {
        onDispose { current.stop() }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view ->
            if (view.drawable !== current) {
                (view.drawable as? AnimatedImageDrawable)?.stop()
                current.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                view.setImageDrawable(current)
                current.start()
            }
        },
        modifier = modifier,
    )
}

@Suppress("DEPRECATION")
@Composable
private fun MovieStage(movie: Movie, modifier: Modifier) {
    AndroidView(
        factory = { MovieView(it) },
        update = { view -> view.play(movie) },
        modifier = modifier,
    )
}

private fun decode(file: File): Any? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeAnimated(file)
    } else {
        @Suppress("DEPRECATION")
        Movie.decodeFile(file.path)
    }

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeAnimated(file: File): AnimatedImageDrawable? {
    val decoded = ImageDecoder.decodeDrawable(
        ImageDecoder.createSource(file),
    ) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
    return decoded as? AnimatedImageDrawable
}

/** API 26–27 的兜底：GIF 按自己的帧时长推进，动画器到末尾直接从头再来。 */
@Suppress("DEPRECATION")
private class MovieView(context: Context) : View(context) {
    private var movie: Movie? = null
    private var animator: ValueAnimator? = null
    private var timeMs = 0

    fun play(next: Movie) {
        if (movie === next && animator?.isRunning == true) return
        animator?.cancel()
        movie = next
        timeMs = 0
        val duration = next.duration().coerceAtLeast(1)
        animator = ValueAnimator.ofInt(0, duration - 1).apply {
            this.duration = duration.toLong()
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                timeMs = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val current = movie ?: return
        val width = current.width()
        val height = current.height()
        if (width <= 0 || height <= 0) return
        val scale = minOf(this.width.toFloat() / width, this.height.toFloat() / height)
        val drawnWidth = width * scale
        val drawnHeight = height * scale
        canvas.save()
        canvas.translate((this.width - drawnWidth) / 2f, (this.height - drawnHeight) / 2f)
        canvas.scale(scale, scale)
        current.setTime(timeMs)
        current.draw(canvas, 0f, 0f)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
