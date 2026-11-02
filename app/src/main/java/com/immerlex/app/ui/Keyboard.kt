package com.immerlex.app.ui

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import com.immerlex.app.R

/**
 * 把落到这块界面上的物理按键交给 [onKey]（返回 true = 这个键用掉了）。
 * [requestOn] 一变就重新要一次焦点：卡片收起、换一章之后组合里可能没有能拿焦点的东西，
 * 焦点会漏到系统那边，方向键就开始挪光标而不是选词。
 */
@Composable
fun KeyCapture(
    onKey: (keyCode: Int) -> Boolean,
    requestOn: Any?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(requestOn) { runCatching { focus.requestFocus() } }
    Box(
        modifier = modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && onKey(event.nativeKeyEvent.keyCode)
            },
        content = content,
    )
}

/** 设置页把键码写成人的话：方向键、空格这类用符号或中文，其余取去掉前缀的英文键名。 */
@Composable
fun keyLabel(keyCode: Int): String = when (keyCode) {
    KeyEvent.KEYCODE_SPACE -> stringResource(R.string.key_space)
    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> stringResource(R.string.key_enter)
    KeyEvent.KEYCODE_DEL -> stringResource(R.string.key_backspace)
    KeyEvent.KEYCODE_DPAD_UP -> "↑"
    KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
    KeyEvent.KEYCODE_DPAD_LEFT -> "←"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
    else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
}
