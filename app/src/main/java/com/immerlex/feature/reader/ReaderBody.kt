package com.immerlex.feature.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.LocalAppearance
import com.immerlex.app.ui.LocalMasteryTones
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord

@Composable
internal fun ChapterBody(
    state: ReaderState,
    layoutState: MutableState<TextLayoutResult?>,
    englishOnly: Boolean,
    onWordTapped: (ReaderWord, Int) -> Unit,
) {
    val wordsByLookup = remember(state.blocks) {
        state.blocks.flatten().filterIsInstance<ReaderSegment.Word>().associateBy { it.word.lookup }
    }
    val tones = LocalMasteryTones.current
    val fontScale = LocalAppearance.current.fontScale
    val annotated = remember(
        state.blocks, state.mode, state.bankWords, state.marks, state.marksAtSec, tones, englishOnly, state.cursor,
    ) {
        buildChapterText(
            state.blocks, state.mode, state.bankWords, tones, state.marks, state.marksAtSec, englishOnly, state.cursor,
        )
    }
    val ranges = remember(annotated) { wordRanges(annotated) }
    val scroll = rememberScrollState()
    val layout = layoutState.value
    // 键盘挪到视野外的词时把它带回来；已经在屏上就不动，免得每次按键整页跳。
    LaunchedEffect(state.cursor, layout) {
        val index = state.cursor ?: return@LaunchedEffect
        val textLayout = layout ?: return@LaunchedEffect
        val range = ranges.getOrNull(index) ?: return@LaunchedEffect
        val line = textLayout.getLineForOffset(range.start)
        val top = textLayout.getLineTop(line)
        val bottom = textLayout.getLineBottom(line)
        val viewport = scroll.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        val visibleTop = scroll.value.toFloat()
        val visibleBottom = visibleTop + viewport
        when {
            top < visibleTop + SCROLL_MARGIN_PX ->
                scroll.animateScrollTo((top - SCROLL_MARGIN_PX).toInt().coerceAtLeast(0))
            bottom > visibleBottom - SCROLL_MARGIN_PX ->
                scroll.animateScrollTo((bottom - viewport + SCROLL_MARGIN_PX).toInt().coerceAtLeast(0))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = annotated,
            style = TextStyle(
                fontSize = fontScale.sp,
                lineHeight = (fontScale * 2f).sp,
                textAlign = TextAlign.Justify,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
                .pointerInput(ranges) {
                    detectTapGestures { offset ->
                        val textLayout = layoutState.value ?: return@detectTapGestures
                        val index = wordIndexAt(ranges, textLayout.getOffsetForPosition(offset))
                            ?: return@detectTapGestures
                        wordsByLookup[ranges[index].lookup]?.word?.let { onWordTapped(it, index) }
                    }
                },
            onTextLayout = { layoutState.value = it },
        )
    }
}

/** 选中的词离视口边太近就提前滚：贴着边线的字看得见半个，还不如先挪。 */
private const val SCROLL_MARGIN_PX = 96f

@Composable
internal fun EmptyChapter(
    generating: Boolean,
    onGenerate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.reader_empty), color = ImmerlexColors.Meta, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onGenerate, enabled = !generating) {
            Text(stringResource(if (generating) R.string.reader_generating else R.string.reader_generate_first))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.reader_go_settings)) }
    }
}

@Composable
internal fun CenteredNote(text: String) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = ImmerlexColors.Meta)
    }
}
