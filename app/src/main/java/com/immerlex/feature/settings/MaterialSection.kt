package com.immerlex.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.readPlainText
import com.immerlex.app.ui.textFor
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 出题素材那一节：一份在别处生成的包，读进来就能给全词库出题用。
 * 读写文件留在界面（SAF 只有界面拿得到），core 只管这份文本。
 */
@Composable
internal fun MaterialSection(vm: MaterialViewModel, snackbar: SnackbarHostState) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> snackbar.showSnackbar(context.textFor(msg)) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = context.readPlainText(uri)
                if (text == null) snackbar.showSnackbar(context.getString(R.string.material_read_failed))
                else vm.importText(text)
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            vm.export { text ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("no output stream")
                }
                snackbar.showSnackbar(context.getString(R.string.material_export_done))
            }
        }
    }

    Text(
        stringResource(
            if (state.words == 0) R.string.material_none else R.string.material_count,
            state.words,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !state.busy) {
            Text(stringResource(if (state.busy) R.string.material_busy else R.string.material_import))
        }
        OutlinedButton(
            onClick = { exportLauncher.launch(EXPORT_NAME) },
            enabled = state.words > 0 && !state.busy,
        ) {
            Text(stringResource(R.string.material_export))
        }
    }
}

private const val EXPORT_NAME = "immerlex-material.json"
