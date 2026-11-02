package com.immerlex.feature.settings

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.KeyCapture
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.keyLabel
import com.immerlex.core.settings.KeyAction
import com.immerlex.core.settings.KeyContext
import com.immerlex.core.settings.KeyMap

/**
 * 一个动作一行，点一下再按一个键就换过来。同一个场景里被抢走的键自动跟原主人互换，
 * 所以这里做完一步就重读整份 [KeyMap]，不自己维护第二份。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(vm: KeyboardViewModel, onBack: () -> Unit) {
    val bindings by vm.bindings.collectAsStateWithLifecycle()
    var capturing by remember { mutableStateOf<KeyAction?>(null) }

    KeyCapture(
        requestOn = capturing,
        modifier = Modifier.fillMaxSize(),
        onKey = { code ->
            val action = capturing
            when {
                action == null -> false
                code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN -> false
                // 录制中按返回是"不算了"，不该顺带退出这一页。
                code == KeyEvent.KEYCODE_BACK -> {
                    capturing = null
                    true
                }
                else -> {
                    vm.bind(action, code)
                    capturing = null
                    true
                }
            }
        },
    ) {
        Scaffold(
            contentWindowInsets = RootHandledInsets,
            topBar = {
                TopAppBar(
                    windowInsets = RootHandledInsets,
                    title = { Text(stringResource(R.string.settings_keyboard)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KeyContext.entries.forEach { context ->
                    Text(
                        stringResource(contextLabel(context)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    KeyAction.of(context).forEach { action ->
                        KeyRow(
                            label = stringResource(actionLabel(action)),
                            keyText = keyLabel(bindings.keyFor(action)),
                            capturing = capturing == action,
                            onClick = { capturing = action },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = vm::reset) { Text(stringResource(R.string.key_reset)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun KeyRow(label: String, keyText: String, capturing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onClick) {
            Text(if (capturing) stringResource(R.string.key_press) else keyText)
        }
    }
}

private fun contextLabel(context: KeyContext): Int = when (context) {
    KeyContext.READER -> R.string.key_reader_section
    KeyContext.QUIZ -> R.string.key_quiz_section
}

private fun actionLabel(action: KeyAction): Int = when (action) {
    KeyAction.READER_PREV_WORD -> R.string.key_reader_prev
    KeyAction.READER_NEXT_WORD -> R.string.key_reader_next
    KeyAction.READER_OPEN_WORD -> R.string.key_reader_open
    KeyAction.READER_EASY -> R.string.grade_easy
    KeyAction.READER_GOOD -> R.string.grade_good
    KeyAction.READER_HARD -> R.string.grade_hard
    KeyAction.READER_AGAIN -> R.string.grade_again
    KeyAction.QUIZ_OPTION_A -> R.string.key_quiz_a
    KeyAction.QUIZ_OPTION_B -> R.string.key_quiz_b
    KeyAction.QUIZ_OPTION_C -> R.string.key_quiz_c
    KeyAction.QUIZ_OPTION_D -> R.string.key_quiz_d
    KeyAction.QUIZ_DOUBT -> R.string.key_quiz_doubt
    KeyAction.QUIZ_NEXT -> R.string.key_quiz_next
}
