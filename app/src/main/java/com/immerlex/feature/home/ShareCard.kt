package com.immerlex.feature.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.schedule.HeatDay
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CARD_WIDTH_PX = 1080f
private const val CARD_HEIGHT_PX = 1440f
private const val MAX_WALL_LINES = 2
private const val WALL_LINE_CHARS = 56
private const val WALL_SEPARATOR = " · "
private val ShareRule = Color(0xFFD9D6CD)

/** 海报上的次要标签统一压深一档：#F5F4F0 纸上原来的 Meta 灰偏淡，字看不清。 */
private val PosterMeta = Color(0xFF63677A)

/**
 * 分享卡永远是纸色，不跟深色主题走——它是一张要发出去的海报，
 * 深色下的纸卡反而会丢字号对比；预览就是导出的那张图，不另画缩放版。
 */
@Composable
fun ShareReportDialog(state: HomeState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var card by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 420.dp),
        ) {
            Box {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ShareCardPreview(card)
                    Spacer(Modifier.height(14.dp))
                    if (failed) {
                        Text(
                            stringResource(R.string.share_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            val bitmap = card ?: return@Button
                            if (busy) return@Button
                            busy = true
                            failed = false
                            scope.launch {
                                val ok = try {
                                    shareBitmap(context, bitmap.asAndroidBitmap())
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                                busy = false
                                if (ok) onDismiss() else failed = true
                            }
                        },
                        enabled = card != null && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.share_action))
                    }
                }
                if (card == null) {
                    ShareCardCapture(state, layer) { captured ->
                        card = captured
                        failed = captured == null
                    }
                }
            }
        }
    }
}

/**
 * 版面按 1080×1440 像素排版，只在这一处 composable 里画一次；
 * 画到 dialog 窗口外面（[offset]）是为了不让它出现在界面上，录制发生在绘制阶段，不受窗口裁切影响。
 */
@Composable
private fun ShareCardCapture(state: HomeState, layer: GraphicsLayer, onCapture: (ImageBitmap?) -> Unit) {
    Box(Modifier.size(1.dp)) {
        ShareCard(
            state = state,
            modifier = Modifier
                .offset(x = 1200.dp)
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                },
        )
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        onCapture(runCatching { layer.toImageBitmap() }.getOrNull())
    }
}

@Composable
private fun ShareCardPreview(image: ImageBitmap?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(CARD_WIDTH_PX / CARD_HEIGHT_PX)
            .clip(RoundedCornerShape(12.dp))
            .background(ImmerlexColors.Paper),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

@Composable
private fun ShareCard(state: HomeState, modifier: Modifier = Modifier) {
    CompositionLocalProvider(
        LocalDensity provides Density(1f, 1f),
        // 海报背景恒为纸色，正文一律吃固定墨色：否则深色主题下默认内容色是接近白的 onSurface，
        // 白字压在纸上看不清（用户 2026-11-11 反馈）。
        LocalContentColor provides ImmerlexColors.Ink,
    ) {
        Column(
            modifier = modifier
                .requiredSize(CARD_WIDTH_PX.dp, CARD_HEIGHT_PX.dp)
                .background(ImmerlexColors.Paper)
                .padding(start = 96.dp, end = 96.dp, top = 56.dp, bottom = 56.dp),
        ) {
            ShareHeader()
            Spacer(Modifier.height(36.dp))
            // 数字前面已经写着"连续"，不再另起一行"连续打卡"。
            ShareHero(state.streak)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ShareRule))
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(110.dp)) {
                ShareStat(state.learnedToday.size, stringResource(R.string.share_card_learned))
                ShareStat(state.recalledToday.size, stringResource(R.string.share_card_recalled))
            }
            Spacer(Modifier.height(20.dp))
            // 两张词墙之间的间距由这一层管：只有一张时不会多空出一截。
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (state.learnedToday.isNotEmpty()) {
                    ShareWall(stringResource(R.string.share_card_learned_words), state.learnedToday)
                }
                if (state.quizzedToday.isNotEmpty()) {
                    ShareWall(stringResource(R.string.share_card_quizzed_words), state.quizzedToday)
                }
            }
            Spacer(Modifier.weight(1f))
            ShareHeat(state.heat)
            Spacer(Modifier.height(22.dp))
            ShareFooter()
        }
    }
}

@Composable
private fun ShareHeader() {
    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd · EEE", Locale.CHINA))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(R.string.app_name),
            fontSize = 34.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            date,
            fontSize = 26.sp,
            color = PosterMeta,
            letterSpacing = 1.sp,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun ShareHero(streak: Int) {
    Row {
        Text(
            stringResource(R.string.share_card_continuous),
            fontSize = 104.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            streak.toString(),
            fontSize = 248.sp,
            lineHeight = 200.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 26.dp).alignByBaseline(),
        )
        Text(
            stringResource(R.string.share_card_day),
            fontSize = 88.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp).alignByBaseline(),
        )
    }
}

@Composable
private fun ShareStat(value: Int, label: String) {
    Column {
        Text(value.toString(), fontSize = 72.sp, fontWeight = FontWeight.Bold, lineHeight = 72.sp)
        Spacer(Modifier.height(12.dp))
        Text(label, fontSize = 26.sp, color = PosterMeta)
    }
}

/**
 * 词墙只列一小截：卡片是固定的 1080×1440 位图，一天二十几个词写全了就没地方放热力图。
 * 数字在上一行已经是全数，这里少列的就直接说"还有几个"。
 * 折行由程序按字数算，不交给文本换行——分隔点落在行首是排版事故。
 */
@Composable
private fun ShareWall(label: String, words: List<String>) {
    val shown = remember(words) { wallLines(words).take(MAX_WALL_LINES) }
    val listed = shown.sumOf { it.size }
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 24.sp, color = PosterMeta, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        shown.forEach { line ->
            Text(
                line.joinToString(WALL_SEPARATOR),
                fontSize = 27.sp,
                lineHeight = 36.sp,
                fontFamily = FontFamily.Serif,
                color = ImmerlexColors.Body,
            )
        }
        if (words.size > listed) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.share_card_more, words.size - listed),
                fontSize = 22.sp,
                color = PosterMeta,
            )
        }
    }
}

/** 每行按字符数折（拉丁字母在衬线 27 号下约 14 像素一字符，两行放得下十个词）。 */
private fun wallLines(words: List<String>): List<List<String>> {
    val lines = mutableListOf<List<String>>()
    var line = mutableListOf<String>()
    var width = 0
    words.forEach { word ->
        if (line.isNotEmpty() && width + word.length + WALL_SEPARATOR.length > WALL_LINE_CHARS) {
            lines += line
            line = mutableListOf()
            width = 0
        }
        line += word
        width += word.length + if (line.size > 1) WALL_SEPARATOR.length else 0
    }
    if (line.isNotEmpty()) lines += line
    return lines
}

@Composable
private fun ShareHeat(days: List<HeatDay>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(R.string.share_card_heat),
            fontSize = 32.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.2.sp,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            stringResource(R.string.share_card_days, days.count { it.words > 0 }),
            fontSize = 24.sp,
            color = PosterMeta,
            modifier = Modifier.alignByBaseline(),
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.chunked(7).forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(heatColour(day.level), RoundedCornerShape(7.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareFooter() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(R.string.app_subtitle),
            fontSize = 24.sp,
            color = PosterMeta,
            letterSpacing = 1.4.sp,
        )
        Text(
            stringResource(R.string.share_card_handle),
            fontSize = 24.sp,
            color = PosterMeta,
        )
    }
}

private fun heatColour(level: Int): Color =
    if (level <= 0) ImmerlexColors.Line else ImmerlexColors.Rust.copy(alpha = 0.2f + 0.2f * level)

/** 分享图只写到 cache 的固定文件名：不累积，也不需要用户选保存位置。 */
private suspend fun shareBitmap(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share")
        dir.mkdirs()
        File(dir, "share-card.png").also { target ->
            FileOutputStream(target).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "share card png" }
            }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}
