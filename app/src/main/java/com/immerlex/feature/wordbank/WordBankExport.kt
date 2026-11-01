package com.immerlex.feature.wordbank

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.immerlex.app.R
import com.immerlex.core.word.wordCsvOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 词库导出走 SAF：`core.word` 只把词表变成字符串，挑位置和写文件留在这一层。
 * 导的是整本词库，与搜索框里正过滤着什么无关。
 */
@Composable
fun rememberWordCsvExport(vm: WordBankViewModel, snackbar: SnackbarHostState): ActivityResultLauncher<String> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val list = runCatching { vm.allWords() }.getOrNull()
                val written = list != null && withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(wordCsvOf(list).toByteArray(Charsets.UTF_8))
                        } ?: error("no output stream")
                    }.isSuccess
                }
                snackbar.showSnackbar(
                    if (written) {
                        context.getString(R.string.bank_export_done, list.orEmpty().size)
                    } else {
                        context.getString(R.string.bank_export_failed)
                    },
                )
            }
        }
    }
}
