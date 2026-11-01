package com.immerlex.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.textFor
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 出题素材那一节：一份在别处生成的包，读进来就能给全词库出题用。
 * 两条路都在——按清单从网上取回，或在手机上选一个本地文件导入，取不动的时候后者是退路。
 * 读写文件留在界面（SAF 只有界面拿得到），core 只管这份文本。
 */
@Composable
internal fun MaterialSection(vm: MaterialViewModel, snackbar: SnackbarHostState) {
    val state by vm.state.collectAsStateWithLifecycle()
    val locked = state.busy || state.fetching
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
    OutlinedTextField(
        value = state.manifestUrl,
        onValueChange = vm::onManifestUrlChange,
        singleLine = true,
        enabled = !locked,
        label = { Text(stringResource(R.string.material_manifest_url)) },
    )
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Button(onClick = vm::fetchFromGitHub, enabled = !locked) {
            Text(stringResource(if (state.fetching) R.string.material_fetching else R.string.material_fetch))
        }
    }
    state.step?.let {
        Text(
            stringResource(R.string.material_fetch_step, it.index, it.total),
            style = MaterialTheme.typography.bodySmall,
            color = ImmerlexColors.Meta,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !locked) {
            Text(stringResource(if (state.busy) R.string.material_busy else R.string.material_import))
        }
        OutlinedButton(
            onClick = { exportLauncher.launch(EXPORT_NAME) },
            enabled = state.words > 0 && !locked,
        ) {
            Text(stringResource(R.string.material_export))
        }
    }
}

private const val EXPORT_NAME = "immerlex-material.json"
