package com.immerlex.feature.stats

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
import com.immerlex.core.schedule.RetentionReport
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
private val PosterMeta = Color(0xFF63677A)
private val PosterRule = Color(0xFFD9D6CD)

/**
 * 战绩炫耀卡：与今日报告卡同一种纸色海报，只是把主角换成"战斗力"这一数字。
 * 预览即导出的那张位图，不另画缩放版；深色主题下正文仍钉死墨色。
 */
@Composable
fun StatsShareDialog(report: RetentionReport, onDismiss: () -> Unit) {
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
                    StatsCardPreview(card)
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
                                    sharePoster(context, bitmap.asAndroidBitmap())
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
                    StatsCardCapture(report, layer) { captured ->
                        card = captured
                        failed = captured == null
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCardCapture(report: RetentionReport, layer: GraphicsLayer, onCapture: (ImageBitmap?) -> Unit) {
    Box(Modifier.size(1.dp)) {
        StatsCard(
            report = report,
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
private fun StatsCardPreview(image: ImageBitmap?) {
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
private fun StatsCard(report: RetentionReport, modifier: Modifier = Modifier) {
    CompositionLocalProvider(
        LocalDensity provides Density(1f, 1f),
        LocalContentColor provides ImmerlexColors.Ink,
    ) {
        Column(
            modifier = modifier
                .requiredSize(CARD_WIDTH_PX.dp, CARD_HEIGHT_PX.dp)
                .background(ImmerlexColors.Paper)
                .padding(start = 96.dp, end = 96.dp, top = 64.dp, bottom = 64.dp),
        ) {
            PosterHeader()
            // 空白摊开匀着留，而不是全堆在页脚上面一条：这张卡的内容天生就比今日报告卡少。
            Spacer(Modifier.height(56.dp))
            Column {
                Text(
                    stringResource(R.string.stats_rank_label),
                    fontSize = 24.sp,
                    color = PosterMeta,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    rankTitle(report.rank),
                    fontSize = 88.sp,
                    lineHeight = 88.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(48.dp))
            Row {
                Text(
                    stringResource(R.string.share_card_strength),
                    fontSize = 96.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    report.strength.toString(),
                    fontSize = 240.sp,
                    lineHeight = 196.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 24.dp).alignByBaseline(),
                )
            }
            Spacer(Modifier.height(40.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(PosterRule))
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(74.dp)) {
                PosterStat(report.canRecall, stringResource(R.string.share_card_can_recall))
                PosterStat(report.mastered, stringResource(R.string.share_card_mastered))
                PosterStat(report.learnedWords, stringResource(R.string.share_card_studied))
            }
            if (report.peak.usable) {
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(R.string.share_card_peak, report.peak.best),
                    fontSize = 26.sp,
                    color = PosterMeta,
                )
            }
            Spacer(Modifier.weight(1f))
            if (report.mostFailedWord != null) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.share_card_nemesis),
                        fontSize = 24.sp,
                        color = PosterMeta,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        report.mostFailedWord + (report.mostFailedGloss?.let { "　$it" } ?: ""),
                        fontSize = 40.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(56.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
            PosterFooter()
        }
    }
}

@Composable
private fun PosterHeader() {
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
private fun PosterStat(value: Int, label: String) {
    Column {
        Text(value.toString(), fontSize = 76.sp, fontWeight = FontWeight.Bold, lineHeight = 76.sp)
        Spacer(Modifier.height(12.dp))
        Text(label, fontSize = 26.sp, color = PosterMeta)
    }
}

@Composable
private fun PosterFooter() {
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

private suspend fun sharePoster(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share")
        dir.mkdirs()
        File(dir, "stats-card.png").also { target ->
            FileOutputStream(target).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "stats card png" }
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
